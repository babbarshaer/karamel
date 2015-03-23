/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.karamel.backend.machines;

import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import org.apache.log4j.Logger;
import se.kth.karamel.backend.running.model.MachineEntity;
import se.kth.karamel.backend.running.model.tasks.ShellCommand;
import se.kth.karamel.backend.running.model.tasks.Task;
import se.kth.karamel.backend.running.model.tasks.Task.Status;
import se.kth.karamel.common.Settings;
import se.kth.karamel.common.exception.KaramelException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.xfer.scp.SCPDownloadClient;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import se.kth.karamel.backend.LogService;
import se.kth.karamel.backend.running.model.tasks.RunRecipeTask;

/**
 *
 * @author kamal
 */
public class SshMachine implements Runnable {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final Logger logger = Logger.getLogger(SshMachine.class);
    private final MachineEntity machineEntity;
    private final String serverPubKey;
    private final String serverPrivateKey;
    private SSHClient client;
    private SCPDownloadClient downloader;
    private long lastHeartbeat = 0;
    private final BlockingQueue<Task> taskQueue = new ArrayBlockingQueue<>(Settings.MACHINES_TASKQUEUE_SIZE);
    private final ConcurrentLinkedQueue<JsonArray> resultsQueue = new ConcurrentLinkedQueue<>();
    private boolean stopping = false;

    public SshMachine(MachineEntity machineEntity, String serverPubKey, String serverPrivateKey) {
        this.machineEntity = machineEntity;
        this.serverPubKey = serverPubKey;
        this.serverPrivateKey = serverPrivateKey;
    }

    public void setStoping(boolean stoping) {
        this.stopping = stoping;
    }

    public void pause() {
        if (machineEntity.getTasksStatus().ordinal() < MachineEntity.TasksStatus.PAUSING.ordinal()) {
            machineEntity.setTasksStatus(MachineEntity.TasksStatus.PAUSING);
        }
    }

    public void resume() {
        if (machineEntity.getTasksStatus() != MachineEntity.TasksStatus.FAILED) {
            machineEntity.setTasksStatus(MachineEntity.TasksStatus.ONGOING);
        }
    }

    @Override
    public void run() {
        logger.info(String.format("Started SSH_Machine to '%s' d'-'", machineEntity.getId()));
        try {
            while (true && !stopping) {

                if (machineEntity.getLifeStatus() == MachineEntity.LifeStatus.CONNECTED
                        && machineEntity.getTasksStatus() == MachineEntity.TasksStatus.ONGOING) {
                    try {
                        logger.debug("Going to take a task from the queue");
                        Task task = taskQueue.take();
                        logger.debug(String.format("Task was taken from the queue.. '%s'", task.getName()));
                        runTask(task);
                    } catch (InterruptedException ex) {
                        if (stopping) {
                            logger.info(String.format("Stopping SSH_Machine to '%s'", machineEntity.getId()));
                            return;
                        } else {
                            logger.error("", ex);
                        }
                    } catch (KaramelException ex) {
                        machineEntity.setTasksStatus(MachineEntity.TasksStatus.FAILED);
                        logger.error("", ex);
                    }
                } else {
                    if (machineEntity.getTasksStatus() == MachineEntity.TasksStatus.PAUSING) {
                        machineEntity.setTasksStatus(MachineEntity.TasksStatus.PAUSED);
                    }
                    try {
                        Thread.sleep(Settings.MACHINE_TASKRUNNER_BUSYWAITING_INTERVALS);
                    } catch (InterruptedException ex) {
                        logger.error("", ex);
                    }
                }
            }
        } finally {
            disconnect();
        }
    }

    public void enqueue(Task task) throws KaramelException {
        logger.debug(String.format("Queuing '%s'", task.toString()));
        try {
            task.setStatus(Status.READY);
            taskQueue.put(task);
        } catch (InterruptedException ex) {
            task.setStatus(Status.FAILED);
            throw new KaramelException(String.format("Couldn't queue task '%s' on machine '%s'", task.getName(), machineEntity.getId()), ex);
        }
    }
    
    /**
     * 
     * @return null if the Queue is empty, otherwise a JsonArray
     */
    public JsonArray getNextResult() {
        return resultsQueue.poll();
    }

    private synchronized void runTask(Task task) throws KaramelException {
        try {
            task.setStatus(Status.ONGOING);
            List<ShellCommand> commands = task.getCommands();

            for (ShellCommand cmd : commands) {
                if (cmd.getStatus() != ShellCommand.Status.DONE) {
                    runSshCmd(cmd, task);
                    if (cmd.getStatus() != ShellCommand.Status.DONE) {
                        task.setStatus(Status.FAILED);
                        break;
                    }
                }
            }
            if (task.getStatus() == Status.ONGOING) {
                task.setStatus(Status.DONE);
            }
        } catch (Exception ex) {
            task.setStatus(Status.FAILED);
            throw new KaramelException(ex);
        }
    }

    private synchronized void runSshCmd(ShellCommand shellCommand, Task task) {
        shellCommand.setStatus(ShellCommand.Status.ONGOING);
        Session session = null;
        try {
            logger.info(machineEntity.getId() + " => " + shellCommand.getCmdStr());

            session = client.startSession();
            Session.Command cmd = session.exec(shellCommand.getCmdStr());
            cmd.join(30, TimeUnit.MINUTES);
            updateHeartbeat();
            if (cmd.getExitStatus() != 0) {
                shellCommand.setStatus(ShellCommand.Status.FAILED);
            } else {
                shellCommand.setStatus(ShellCommand.Status.DONE);
                if (task instanceof RunRecipeTask) {
                    RunRecipeTask rrt = (RunRecipeTask) task;
                    try {
                        JsonArray results = downloadResultsScp(rrt.getCookbook(), rrt.getRecipe());
                        rrt.setResults(results);
                    } catch (JsonParseException p) {
                        logger.debug("Results were not a valid json document: " + rrt.getCookbook() + "::" + rrt.getRecipe());                        
                    } catch (IOException e) {
                        logger.debug("No results were (able to be) downloaded for: " + rrt.getCookbook() + "::" + rrt.getRecipe());
                    }
                }
            }
            LogService.serializeTaskLogs(task, machineEntity.getPublicIp(), cmd.getInputStream(), cmd.getErrorStream());

        } catch (ConnectionException | TransportException ex) {
            logger.error(String.format("Couldn't excecute command on client '%s' ", machineEntity.getId()), ex);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (TransportException | ConnectionException ex) {
                    logger.error(String.format("Couldn't close ssh session to '%s' ", machineEntity.getId()), ex);
                }
            }
        }
    }

    /**
     * http://unix.stackexchange.com/questions/136165/java-code-to-copy-files-from-one-linux-machine-to-another-linux-machine
     *
     * @param session
     * @param cookbook
     * @param recipe
     */
    private synchronized JsonArray downloadResultsScp(String cookbook, String recipe) throws IOException {
        String remoteFile = "~/" + cookbook + "__" + recipe + ".out";
        SCPFileTransfer scp = client.newSCPFileTransfer();
        String localResultsFile = Settings.KARAMEL_TMP_PATH + File.separator + cookbook + "__" + recipe + ".out";
        File f = new File(localResultsFile);
        // TODO - should move this to some initialization method
        f.mkdirs();
        if (f.exists()) {
            f.delete();
        }
        // TODO: error checking here...
        scp.download(remoteFile, localResultsFile);
        JsonReader reader = new JsonReader(new FileReader(localResultsFile));
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(reader).getAsJsonArray();
    }

    private synchronized boolean connect() throws KaramelException {
        try {
            KeyProvider keys = null;
            client = new SSHClient();
            client.addHostKeyVerifier(new PromiscuousVerifier());
            keys = client.loadKeys(serverPrivateKey, serverPubKey, null);
            logger.info(String.format("connecting to '%s'...", machineEntity.getId()));
            try {
                client.connect(machineEntity.getPublicIp(), machineEntity.getSshPort());
            } catch (IOException ex) {
                logger.warn(String.format("Opps!! coudln't connect to '%s' :@", machineEntity.getId()));
                logger.debug(ex);
            }
            if (client.isConnected()) {
                logger.info(String.format("Yey!! connected to '%s' ^-^", machineEntity.getId()));
                client.authPublickey(machineEntity.getSshUser(), keys);
                machineEntity.setLifeStatus(MachineEntity.LifeStatus.CONNECTED);
                return true;
            } else {
                logger.error(String.format("Mehh!! no connection to '%s', is the port '%d' open?", machineEntity.getId(), machineEntity.getSshPort()));
                machineEntity.setLifeStatus(MachineEntity.LifeStatus.UNREACHABLE);
                return false;
            }
        } catch (IOException e) {
            throw new KaramelException(e);
        }
    }

    public synchronized void disconnect() {
        logger.info(String.format("Closing ssh session to '%s'", machineEntity.getId()));
        try {
            client.close();
        } catch (IOException ex) {
        }
    }

    public synchronized boolean ping() throws KaramelException {
        if (lastHeartbeat < System.currentTimeMillis() - Settings.SSH_PING_INTERVAL) {
            if (client != null && client.isConnected()) {
                updateHeartbeat();
                return true;
            } else {
                return connect();
            }
        } else {
            return true;
        }
    }

    private void updateHeartbeat() {
        lastHeartbeat = System.currentTimeMillis();
    }
}

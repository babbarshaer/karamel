/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.karamel.client.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import se.kth.karamel.backend.ClusterDefinitionService;
import se.kth.karamel.backend.ClusterService;
import se.kth.karamel.backend.command.CommandResponse;
import se.kth.karamel.backend.command.CommandService;
import se.kth.karamel.backend.launcher.amazon.Ec2Context;
import se.kth.karamel.backend.launcher.amazon.Ec2Launcher;
import se.kth.karamel.backend.running.model.ClusterEntity;
import se.kth.karamel.backend.running.model.GroupEntity;
import se.kth.karamel.backend.running.model.MachineEntity;
import se.kth.karamel.backend.running.model.serializers.ClusterEntitySerializer;
import se.kth.karamel.backend.running.model.serializers.GroupEntitySerializer;
import se.kth.karamel.backend.running.model.serializers.MachineEntitySerializer;
import se.kth.karamel.backend.running.model.serializers.ShellCommandSerializer;
import se.kth.karamel.backend.running.model.serializers.DefaultTaskSerializer;
import se.kth.karamel.backend.running.model.tasks.AptGetEssentialsTask;
import se.kth.karamel.backend.running.model.tasks.CollectResultsTask;
import se.kth.karamel.backend.running.model.tasks.InstallBerkshelfTask;
import se.kth.karamel.backend.running.model.tasks.MakeSoloRbTask;
import se.kth.karamel.backend.running.model.tasks.RunRecipeTask;
import se.kth.karamel.backend.running.model.tasks.ShellCommand;
import se.kth.karamel.backend.running.model.tasks.VendorCookbookTask;
import se.kth.karamel.common.exception.KaramelException;
import se.kth.karamel.cookbook.metadata.GithubCookbook;
import se.kth.karamel.common.Confs;
import se.kth.karamel.common.Ec2Credentials;
import se.kth.karamel.common.Settings;
import se.kth.karamel.common.SshKeyPair;
import se.kth.karamel.common.SshKeyService;

/**
 * Implementation of the Karamel Api for UI
 *
 * @author kamal
 */
public class KaramelApiImpl implements KaramelApi {

  private static final ClusterService clusterService = ClusterService.getInstance();

  @Override
  public String commandCheatSheet() throws KaramelException {
    return CommandService.processCommand("help").getResult();
  }

  @Override
  public CommandResponse processCommand(String command, String... args) throws KaramelException {
    return CommandService.processCommand(command, args);
  }

  @Override
  public String getCookbookDetails(String cookbookUrl, boolean refresh) throws KaramelException {
    if (refresh) {
      GithubCookbook cb = CookbookCache.load(cookbookUrl);
      return cb.getMetadataJson();
    } else {
      GithubCookbook cb = CookbookCache.get(cookbookUrl);
      return cb.getMetadataJson();
    }
  }

  @Override
  public String jsonToYaml(String json) throws KaramelException {
    return ClusterDefinitionService.jsonToYaml(json);
  }

  @Override
  public String yamlToJson(String yaml) throws KaramelException {
    return ClusterDefinitionService.yamlToJson(yaml);
  }

  @Override
  public Ec2Credentials loadEc2CredentialsIfExist() throws KaramelException {
    Confs confs = Confs.loadKaramelConfs();
    return Ec2Launcher.readCredentials(confs);
  }

  @Override
  public boolean updateEc2CredentialsIfValid(Ec2Credentials credentials) throws KaramelException {
    Ec2Context context = Ec2Launcher.validateCredentials(credentials);
    Confs confs = Confs.loadKaramelConfs();
    confs.put(Settings.EC2_ACCOUNT_ID_KEY, credentials.getAccountId());
    confs.put(Settings.EC2_ACCESSKEY_KEY, credentials.getAccessKey());
    confs.writeKaramelConfs();
    clusterService.registerEc2Context(context);
    return true;
  }

  @Override
  public String getClusterStatus(String clusterName) throws KaramelException {
    ClusterEntity clusterManager = clusterService.clusterStatus(clusterName);
    Gson gson = new GsonBuilder().
            registerTypeAdapter(ClusterEntity.class, new ClusterEntitySerializer()).
            registerTypeAdapter(MachineEntity.class, new MachineEntitySerializer()).
            registerTypeAdapter(GroupEntity.class, new GroupEntitySerializer()).
            registerTypeAdapter(ShellCommand.class, new ShellCommandSerializer()).
            registerTypeAdapter(RunRecipeTask.class, new DefaultTaskSerializer()).
            registerTypeAdapter(CollectResultsTask.class, new DefaultTaskSerializer()).
            registerTypeAdapter(MakeSoloRbTask.class, new DefaultTaskSerializer()).
            registerTypeAdapter(VendorCookbookTask.class, new DefaultTaskSerializer()).
            registerTypeAdapter(AptGetEssentialsTask.class, new DefaultTaskSerializer()).
            registerTypeAdapter(InstallBerkshelfTask.class, new DefaultTaskSerializer()).
            setPrettyPrinting().
            create();
    String json = gson.toJson(clusterManager);
    return json;
  }

  @Override
  public void pauseCluster(String clusterName) throws KaramelException {
    clusterService.pauseCluster(clusterName);
  }

  @Override
  public void resumeCluster(String clusterName) throws KaramelException {
    clusterService.resumeCluster(clusterName);
  }

  @Override
  public void purgeCluster(String clusterName) throws KaramelException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void startCluster(String json) throws KaramelException {
    clusterService.startCluster(json);
  }

  @Override
  public String getInstallationDag(String clusterName) throws KaramelException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public SshKeyPair loadSshKeysIfExist() throws KaramelException {
    Confs confs = Confs.loadKaramelConfs();
    SshKeyPair sshKeys = SshKeyService.loadSshKeys(confs);
    return sshKeys;
  }

  @Override
  public SshKeyPair loadSshKeysIfExist(String clusterName) throws KaramelException {
    Confs confs = Confs.loadAllConfsForCluster(clusterName);
    SshKeyPair sshKeys = SshKeyService.loadSshKeys(confs);
    return sshKeys;
  }

  @Override
  public SshKeyPair generateSshKeysAndUpdateConf() throws KaramelException {
    SshKeyPair sshkeys = SshKeyService.generateAndStoreSshKeys();
    Confs confs = Confs.loadKaramelConfs();
    confs.put(Settings.SSH_PRIKEY_PATH_KEY, sshkeys.getPrivateKeyPath());
    confs.put(Settings.SSH_PUBKEY_PATH_KEY, sshkeys.getPublicKeyPath());
    confs.writeKaramelConfs();
    return sshkeys;
  }

  @Override
  public SshKeyPair generateSshKeysAndUpdateConf(String clusterName) throws KaramelException {
    SshKeyPair sshkeys = SshKeyService.generateAndStoreSshKeys(clusterName);
    Confs confs = Confs.loadJustClusterConfs(clusterName);
    confs.put(Settings.SSH_PRIKEY_PATH_KEY, sshkeys.getPrivateKeyPath());
    confs.put(Settings.SSH_PUBKEY_PATH_KEY, sshkeys.getPublicKeyPath());
    confs.writeClusterConfs(clusterName);
    return sshkeys;
  }

  @Override
  public void registerSshKeys(SshKeyPair keypair) throws KaramelException {
    clusterService.registerSshKeyPair(keypair);
    Confs confs = Confs.loadKaramelConfs();
    confs.put(Settings.SSH_PRIKEY_PATH_KEY, keypair.getPrivateKeyPath());
    confs.put(Settings.SSH_PUBKEY_PATH_KEY, keypair.getPublicKeyPath());
    confs.writeKaramelConfs();
  }

  @Override
  public void registerSshKeys(String clusterName, SshKeyPair keypair) throws KaramelException {
    clusterService.registerSshKeyPair(clusterName, keypair);
    Confs confs = Confs.loadJustClusterConfs(clusterName);
    confs.put(Settings.SSH_PRIKEY_PATH_KEY, keypair.getPrivateKeyPath());
    confs.put(Settings.SSH_PUBKEY_PATH_KEY, keypair.getPublicKeyPath());
    confs.writeClusterConfs(clusterName);
  }

}

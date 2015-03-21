/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.karamel.client.model;

import se.kth.karamel.common.Settings;

/**
 *
 * @author kamal
 */
public class Ec2 extends Provider {

    private String type;
    private String region;
    private String image;
    private Float price;

    /**
     * Challenges with VPC/subnetId - http://clarkdave.net/2013/05/creating-ec2-instances-in-an-amazon-vpc-using-chef-and-knife/
     */
//    private String subnetId;

//    public String getSubnetId() {
//        return subnetId;
//    }
//
//    public void setSubnetId(String subnetId) {
//        this.subnetId = subnetId;
//    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Float getPrice() {
        return price;
    }

    public void setPrice(Float price) {
        this.price = price;
    }

    public static Ec2 makeDefault() {
        Ec2 ec2 = new Ec2();
        return ec2.applyDefaults();
    }

    @Override
    public Ec2 applyDefaults() {
        Ec2 clone = cloneMe();
        if (clone.getUsername() == null) {
            clone.setUsername(Settings.PROVIDER_EC2_DEFAULT_USERNAME);
        }
        if (clone.getImage() == null) {
            clone.setImage(Settings.PROVIDER_EC2_DEFAULT_IMAGE);
        }
        if (clone.getRegion() == null) {
            clone.setRegion(Settings.PROVIDER_EC2_DEFAULT_REGION);
        }
        if (clone.getType() == null) {
            clone.setType(Settings.PROVIDER_EC2_DEFAULT_TYPE);
        }
//        if (clone.getSubnetId() == null) {
//            clone.setSubnetId(Settings.PROVIDER_EC2_DEFAULT_SUBNET_ID);
//        }
        return clone;
    }

    @Override
    public Ec2 cloneMe() {
        Ec2 ec2 = new Ec2();
        ec2.setUsername(getUsername());
        ec2.setImage(image);
        ec2.setRegion(region);
        ec2.setType(type);
        ec2.setPrice(price);
//        ec2.setSubnetId(subnetId);
        return ec2;
    }

    @Override
    public Ec2 applyParentScope(Provider parentScopeProvider) {
        Ec2 clone = cloneMe();
        if (parentScopeProvider instanceof Ec2) {
            Ec2 parentEc2 = (Ec2) parentScopeProvider;
            if (clone.getUsername() == null) {
                clone.setUsername(parentEc2.getUsername());
            }
            if (clone.getImage() == null) {
                clone.setImage(parentEc2.getImage());
            }
            if (clone.getRegion() == null) {
                clone.setRegion(parentEc2.getRegion());
            }
            if (clone.getType() == null) {
                clone.setType(parentEc2.getType());
            }
            if (clone.getPrice() == null) {
                clone.setPrice(parentEc2.getPrice());
            }
//            if (clone.getSubnetId() == null) {
//                clone.setSubnetId(parentEc2.getSubnetId());
//            }            
        }
        return clone;
    }

}

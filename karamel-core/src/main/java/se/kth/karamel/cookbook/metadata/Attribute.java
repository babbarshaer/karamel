/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package se.kth.karamel.cookbook.metadata;

import com.google.gson.annotations.SerializedName;

/**
 *
 * @author kamal
 */
public class Attribute {
  String name;
  String displayName;
  String type;
  String description;
  @SerializedName("default")
  String defaultVal;
  String required;

  public void setDefault(String defaultVal) {
    this.defaultVal = defaultVal;
  }

  public String getDefault() {
    return defaultVal;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setRequired(String required) {
    this.required = required;
  }

  public String getRequired() {
    return required;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
  
}

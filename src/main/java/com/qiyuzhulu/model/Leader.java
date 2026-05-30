package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 领袖信息 */
public class Leader {
    private String name;
    private String title;
    @JsonProperty("birth_death")
    private String birthDeath;
    private String background;
    private String trait;
    private String style;

    public Leader() {}

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getBirthDeath() { return birthDeath; }
    public void setBirthDeath(String v) { this.birthDeath = v; }
    public String getBackground() { return background; }
    public void setBackground(String v) { this.background = v; }
    public String getTrait() { return trait; }
    public void setTrait(String v) { this.trait = v; }
    public String getStyle() { return style; }
    public void setStyle(String v) { this.style = v; }
}

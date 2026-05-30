package com.qiyuzhulu.model;

/** 外交关系条目 */
public class DiplomaticRelation {
    private int score;          // 关系值（正=友好）
    private String pact;        // non_aggression / alliance / trade / null
    private int turnsLeft;      // 协议剩余回合

    public DiplomaticRelation() {}

    public int getScore() { return score; }
    public void setScore(int v) { this.score = v; }
    public String getPact() { return pact; }
    public void setPact(String v) { this.pact = v; }
    public int getTurnsLeft() { return turnsLeft; }
    public void setTurnsLeft(int v) { this.turnsLeft = v; }
}

package edu.buffalo.cse.cse486586.simpledynamo;

/**
 * Created by vivek on 4/25/17.
 */

public class NodeDetails implements java.io.Serializable {
    String PPort="";
    String Sport="";
    String secondPPort="";
    String secondSPort="";
    String status="";
    String nodeId="";


    public NodeDetails(String PPort, String sport, String secondPPort, String secondSPort, String status,String nodeId) {
        this.PPort = PPort;
        this.Sport = sport;
        this.secondPPort = secondPPort;
        this.secondSPort = secondSPort;
        this.status = status;
        this.nodeId = nodeId;
    }

    @Override
    public String toString() {
        return "NodeDetails{" +
                "PPort='" + PPort + '\'' +
                ", secondPPort='" + secondPPort + '\'' +
                ", Sport='" + Sport + '\'' +
                ", secondSPort='" + secondSPort + '\'' +
                ", status='" + status + '\'' +
                ", nodeId='" + nodeId + '\'' +
                '}';
    }


    public String getPPort() {
        return PPort;
    }

    public void setPPort(String PPort) {
        this.PPort = PPort;
    }

    public String getSecondPPort() {
        return secondPPort;
    }

    public void setSecondPPort(String secondPPort) {
        this.secondPPort = secondPPort;
    }

    public String getSport() {
        return Sport;
    }

    public void setSport(String sport) {
        Sport = sport;
    }

    public String getsecondSPort() {
        return secondSPort;
    }

    public void setsecondSPort(String secondSPort) {
        this.secondSPort = secondSPort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public String getnodeId() {
        return nodeId;
    }



}

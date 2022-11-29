package hu.bme.mit.violation2test;

public class Edge {
    public String Assumption;
    public int Startline;
    public String Assumptionresultfunction;
    public String Type;
    public boolean toUse = false;

    public Edge(){
        Assumption = "";
        Startline = 0;
        Assumptionresultfunction = "fgv";
        Type = "";
    }
    public Edge(String a, int sl, String arf, String t){
        Assumption = a;
        Startline = sl;
        Assumptionresultfunction = arf;
        Type = t;
    }
    public void Writer(){
        if(toUse)System.out.println("Assumption: " + Assumption + "\tAssumptionresultfunction: " + Assumptionresultfunction + "\tStartline: " + Startline);
    }
}

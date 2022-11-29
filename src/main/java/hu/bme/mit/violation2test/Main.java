package hu.bme.mit.violation2test;


import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    //ez a részlet fog bekerülni a C kódba a main elé, hogy futtatható legyen (nondet fv megvalósítások)
    public static StringBuilder headerbe = new StringBuilder();

    //ebbe lesz beolvasva a C kód
    public static StringBuilder kod = new StringBuilder();

    //soronként tárolva a kódot
    public static ArrayList<String> sorok = new ArrayList<>();

    //élek adatainak eltárolása beolvasás után ebben
    static ArrayList<Edge> edges = new ArrayList<>();
//    @Parameter(names = "--codeFolder", required = true, description = "Ide add meg annak a mappanak a nevet, amiben talalhato a C kod!")
//    String codeFolder;
    @Parameter(names = "--codeFile", required = true, description = "Ide add meg annak a filenak a nevet, ami a C kod!")
    String codeFile;
    @Parameter(names = "--gmlFile", required = true, description = "Ide add meg a witness file eleresi utjat!")
    String gmlFile;
    @Parameter(names = "--targetFolder", required = true, description = "Ide add meg a cel mappa eleresi utjat!")
    String targetFolder;

    public static void main(final String[] args) {
        final Main mainApp = new Main();
        mainApp.run(args);
    }

    public void run(String[] args) {
        try {
            JCommander.newBuilder().addObject(this).programName("graphmlparser").build().parse(args);
        } catch (final ParameterException ex) {
            System.out.println("Invalid parameters, details:");
            System.out.println(ex.getMessage());
            ex.usage();
            return;
        }
        //String codeFolder = "C:\\egyetem masolat\\felev5\\temalab\\c-tesztek\\count_up_down-2";
        //String gmlFolder = "C:\\egyetem masolat\\felev5\\temalab\\c-tesztek\\count_up_down-2";
        //String codeFile = "count_up_down-2.c";
        //String gmlFile = "c-u-d-witness.xml";
        //String targetFolder = "C:\\egyetem masolat\\felev5\\temalab\\futasra\\" + codeFile.substring(0, codeFile.length() - 2) + "-out";
        if(!ReadXML(gmlFile, codeFile)) return;
        Init();
        ReadCode(/*codeFolder, */codeFile);
        Checker();
        WriteCode(targetFolder);
        CompileCprog(targetFolder);

        //System.out.println(headerbe.toString());
//        for(int i = 0; i < edges.size(); i++){
//            System.out.print((i+1) + ". ");
//            edges.get(i).Writer();
//        }
    }

    //beolvas egy graphml filet és el is tárolja
    public static boolean ReadXML(String gmlFile, String keres){
        boolean violation = true;
        int idx = 0;
        for (int i = 0; i < keres.length(); i++) {
            if(keres.charAt(i) == '\\') idx = i;
        }
        boolean joFile = false;
        File myFile = new File(gmlFile);
        Scanner myReader = null;
        try {
            myReader = new Scanner(myFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        while (myReader.hasNextLine()) {
            String data = myReader.nextLine();
            if(data.contains(keres.substring(idx + 1))){
                joFile = true;
                break;
            }
        }
        if(!joFile) return false;


        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(gmlFile);
            //keressük, hogy correctness witness-e
            //data node-e
            NodeList dataAttributes = doc.getElementsByTagName("data");
            for(int i = 0; i < dataAttributes.getLength(); i++){
                Node data = dataAttributes.item(i);
                if (data.getNodeType() == Node.ELEMENT_NODE){
                    Element d = (Element) data;
                    if (d.getAttribute("key").equals("witness-type") && d.getTextContent().contains("correctness")){
                        System.out.println("correctness");
                        violation = false;
                    }
                    if (d.getAttribute("key").equals("witness-type") && d.getTextContent().contains("violation")){
                        System.out.println("violation");
                        violation = true;
                    }
                }
//                dataAttributes.item(i).getAttributes();//megnézni, hogy correctnesswitness-e
            }
            if(!violation)return false;
            NodeList edges = doc.getElementsByTagName("edge");
            //végig megyünk az éleken
            for(int i = 0; i < edges.getLength(); i++){
                Node e = edges.item(i);
                if(e.getNodeType() == Node.ELEMENT_NODE){
                    Element edge = (Element) e;
                    NodeList datas = edge.getChildNodes();
                    Edge newEdge = new Edge();
                    //végig megyünk egy adott él adatain
                    for(int j = 0; j < datas.getLength(); j++){
                        Node d = datas.item(j);
                        if(d.getNodeType() == Node.ELEMENT_NODE){
                            Element data = (Element) d;
                            if(data.getAttribute("key").equals("assumption")) {
                                Pattern pattern = Pattern.compile("==");
                                Matcher matcher = pattern.matcher(data.getTextContent());
                                if(matcher.find()){
                                    int strtidx = matcher.end();
                                    newEdge.Assumption = data.getTextContent().substring(strtidx);
                                }
                            }
                            if(data.getAttribute("key").equals("assumption.resultfunction")) {
                                Pattern pattern = Pattern.compile("__VERIFIER_nondet_" + "[a-z]+");
                                Matcher matcher = pattern.matcher(data.getTextContent());
                                matcher.find();
                                int endidx = matcher.end();
                                String funcname = data.getTextContent().substring(0, endidx);
                                newEdge.Type = funcname.substring(("__VERIFIER_nondet_").length());
                                if(newEdge.Type.startsWith("u"))newEdge.Type = "unsigned " + newEdge.Type.substring(1);
                                newEdge.Assumptionresultfunction = funcname;
                            }
                            if(data.getAttribute("key").equals("startline")) {
                                newEdge.Startline = Integer.parseInt(data.getTextContent());
                                newEdge.toUse = true;
                            }
                        }
                    }
                    //használható éleket felvesszük
                    if(newEdge.toUse) Main.edges.add(newEdge);
                }
            }
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
        return joFile;
    }

    //C forrásfileba a nondet fv-ek megvalósítására írt kód összerakása
    public static void Init(){
        if(edges.size() == 0) return;
        Collections.sort(edges,new Comparator<>() {
            @Override
            public int compare(Edge e1, Edge e2) {
                return e1.Assumptionresultfunction.compareTo(e2.Assumptionresultfunction);
            }
        });
        int diff = 0;
        headerbe.append(edges.get(0).Type + " tomb" + diff + "[" + (int) edges.stream().filter(c -> edges.get(0).Type.equals(c.Type)).count() + "] = {");
        for(int i = 0; i < edges.size() - 1; i++){
            headerbe.append(edges.get(i).Assumption);
            if(!edges.get(i).Assumptionresultfunction.equals(edges.get(i + 1).Assumptionresultfunction)) {
                headerbe.append("};\nint idx" + diff + " = 0;\n" + edges.get(i).Type + " " + edges.get(i).Assumptionresultfunction + "(){\n\treturn tomb" + diff + "[idx" + diff + "++];\n}\n");
                diff++;
                String seged = edges.get(i + 1).Type;
                headerbe.append(edges.get(i + 1).Type + " tomb" + diff + "[" + (int) edges.stream().filter(c -> seged.equals(c.Type)).count() + "] = {");
            }else{
                headerbe.append(", ");
            }
        }
        headerbe.append(edges.get(edges.size() - 1).Assumption + "};\nint idx" + diff + " = 0;\n" + edges.get(edges.size() - 1).Type + " " + edges.get(edges.size() - 1).Assumptionresultfunction + "(){\n\treturn tomb" + diff + "[idx" + diff + "++];\n}\n");
    }

    //beolvasásra kerül a c file tartalma
    public static void ReadCode(/*String codeFolder, */String codeFile){
        try {
            File myFile = new File(codeFile);
            Scanner myReader = new Scanner(myFile);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
//                int lastvmi = data.lastIndexOf('{');
//                int lastassertf = data.lastIndexOf("__assert_fail");
//                String seged1 = "";
//                if(lastvmi > -1 && lastassertf > -1){
//                    seged1  = data.substring(lastvmi, lastassertf + ("__assert_fail").length());
//                }
//                if(data.contains(seged1) && lastvmi > -1 && lastassertf > -1){
//                    String val = data.substring(0,lastvmi + 1) + "_assert" + data.substring(lastassertf + ("__assert_fail").length());
//                    data = val;
//
//                    int lastComa = data.lastIndexOf(',');
//                    int lastBracket = data.lastIndexOf(')');
//                    String val1 = data.substring(0,lastComa) + data.substring(lastBracket);
//                    data = val1;
//                    //System.out.println(data + "\n");
//                }
                sorok.add(data);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("Hiba van a beolvasassal.");
            e.printStackTrace();
        }
    }

    //C file átnézése, ha a witnessben nem derült ki, hogy milyen fgvt kell hívni
    public static void Checker(){
        for(int i = 0; i < edges.size(); i++){
            if(edges.get(i).Assumptionresultfunction.equals("fgv")){
                int sor = edges.get(i).Startline;

                //2 sor lett hozzá adva
                int typeEleje = sorok.get(sor + 1).lastIndexOf("__VERIFIER_nondet_");

                Pattern pattern = Pattern.compile("__VERIFIER_nondet_" + "[a-z]+");
                Matcher matcher = pattern.matcher(sorok.get(sor - 1));
                matcher.find();
                int endidx = matcher.end();
                int startidx = matcher.start();
                String funcname = sorok.get(sor + 1).substring(startidx, endidx);
                edges.get(i).Type = funcname.substring(("__VERIFIER_nondet_").length());
                if(edges.get(i).Type.startsWith("u")) edges.get(i).Type = "unsigned " + edges.get(i).Type.substring(1);
                edges.get(i).Assumptionresultfunction = funcname;

            }
        }
    }

    //C és header fileok megírása
    public static void WriteCode(String targetFolder){
        kod.append("#include <assert.h>\n#include <stdbool.h>\n#include <stdio.h>\n#include \"nondetfvek.h\"\n");
        for(int i = 0; i < sorok.size(); i++){
            kod.append(sorok.get(i));
            kod.append("\n");
        }
        File tFolder = new File(targetFolder);
        try{
            if(!tFolder.exists())
                new File(targetFolder).mkdir();
        }catch (Exception e){

        }
        String[] files = tFolder.list();
        if(files.length > 0){
            final File[] fileok = tFolder.listFiles();
            for (File f: fileok) f.delete();
        }
        //c file kiírása
        try {
            FileWriter myWriter = new FileWriter(targetFolder + "/main.c");
            myWriter.write(kod.toString());
            myWriter.close();
        } catch (IOException e) {
            System.out.println("Hiba van a kiiratassal.");
            e.printStackTrace();
        }

        //header file létrehozása és megírása
        try {
            FileWriter myWriter = new FileWriter(targetFolder + "/nondetfvek.h");
            myWriter.write("#ifndef NONDETFVEK_H_INCLUDED\n" +
                    "#define NONDETFVEK_H_INCLUDED\n");
            myWriter.write(headerbe.toString());
            myWriter.write("#endif");
            myWriter.close();
        } catch (IOException e) {
            System.out.println("Hiba van a kiiratassal.");
            e.printStackTrace();
        }
    }

    //fordítás és futtatás
    public static void CompileCprog(String targetFolder){

        File dir = new File(targetFolder);

        String[] files = dir.list();
        if(files.length > 2){
            final File[] fileok = dir.listFiles();
            for (File f: fileok){
                if((!f.getName().equals("main.c")) && (!f.getName().equals("nondetfvek.h")))
                    f.delete();
            }
        }

        String newTargetFolder ="";
        for (int i = 0; i < targetFolder.length(); i++) {
            if(targetFolder.charAt(i) == '\\'){
                newTargetFolder += "\\\\";
            }else{
                newTargetFolder += targetFolder.charAt(i);
            }
        }

        ProcessBuilder builder = new ProcessBuilder(
                "sh", "-c", "cd " + targetFolder + " && gcc main.c -o main && ./main");
        builder.redirectErrorStream(true);
        Process p = null;
        try {
            p = builder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while (true) {
            try {
                line = r.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (line == null) { break; }
            System.out.println(line);
        }
    }
}
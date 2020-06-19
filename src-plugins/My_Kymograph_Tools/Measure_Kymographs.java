package My_Kymograph_Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileInfo;
import ij.io.ImageReader;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;


public class Measure_Kymographs implements PlugIn {

    String filename;

    public void run(String arg){

        //Choose a Folder
        String baseDirectory = IJ.getDirectory("");

        //Open scale.txt and import frameInterval and pixelWidth
        Boolean oldScaleFormat = isOldScaleFormat(baseDirectory+"scale.txt");
        double[] scale = new double[2];
        if(oldScaleFormat){
            scale=getOldScaleFromFile(baseDirectory+"scale.txt");
        }else{
            scale= getScaleFromFile(baseDirectory+"scale.txt");
        }
        double pixelWidth = scale[0];
        double frameInterval = scale[1];
        IJ.log("pixel width: "+ scale[0]+ "  Frame Interval "+scale[1]);
        // Create a Results.txt file with appropriate headings
        String resultsFileName = makeResultsFile(frameInterval,pixelWidth,baseDirectory);

        //Check to save ROIs
        boolean saveROIFile = makeYesNoDialog("Save ROIs", "Do you want to save ROIs?");

        //For each file containing .tif in folder:
        FileNameExtensionFilter filter = new FileNameExtensionFilter(null,".tif");
        String[] filesInFolder = new File(baseDirectory).list();
        for(int i = 0; i <filesInFolder.length;i++){
             if (filesInFolder[i].contains(".tif")){
                 IJ.open(baseDirectory+filesInFolder[i]);

                 //Draw and Save ROIs
                 Roi[] roiArray = makeROIs();
                 ImagePlus imp = WindowManager.getCurrentImage();
                 imp.close();
                 if (saveROIFile) {
                     RoiManager roiManager = RoiManager.getRoiManager();
                     roiManager.runCommand("Save", IJ.getDirectory("current") + "Kymograph_"+i + ".zip");
                 }

                 //Calculate Velocity, Distance and Time for each line ROI
                 for(int j = 0; j < roiArray.length; j++ ){
                     Point[] point = roiArray[j].getContainedPoints();
                     int x1 = point[0].x;
                     int y1 = point[0].y;
                     int x2 = point[point.length-1].x;
                     int y2 = point[point.length-1].y;

                     double distance = Math.abs((x2 - x1)*pixelWidth);
                     double time = Math.abs((y2 - y1)*frameInterval);
                     double velocity = distance/time;

                     //Output to .txt file
                     writeToResults(i,j,distance,time,velocity,resultsFileName);

                 }

             }

        }

    }

    private void writeToResults(int i, int j, double distance, double time, double velocity, String resultsFileName){
        try{
            FileWriter fileWriter = new FileWriter(resultsFileName,true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.newLine();
            bufferedWriter.write(i+"    "+j+"   "+distance+"    "+time+"    "+velocity);
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '" + resultsFileName + "'");
        }
    }

    private String makeResultsFile(double frameInterval, double pixelWidth, String directory){

        String CreateName = Paths.get( directory , "Results_0.txt").toString();
        File resultsFile = new File(CreateName);

        int i = 1;
        while (resultsFile.exists()){
            CreateName= Paths.get( directory , "Results_"+i+".txt").toString();
            resultsFile = new File(CreateName);
            IJ.log(CreateName);
            i++;
        }
        try{
            FileWriter fileWriter = new FileWriter(CreateName,true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.newLine();
            bufferedWriter.write("File= " + directory);
            bufferedWriter.newLine();
            bufferedWriter.write("Pixel Width: " + pixelWidth+ "    Frame Interval: " + frameInterval);
            bufferedWriter.newLine();
            bufferedWriter.write("KymoGraph Number  ROI Number  Distance    Time    Velocity");
            bufferedWriter.newLine();
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '" + CreateName + "'");
        }
        return CreateName;
    }

    public Roi[]  makeROIs(){

        IJ.setTool("line");
        RoiManager roiManager = RoiManager.getRoiManager();
        ImagePlus imp = WindowManager.getCurrentImage();
        roiManager.runCommand(imp,"Show All without labels");
        if(roiManager.getCount()!=0){
        roiManager.runCommand("Delete");
        }
        new WaitForUserDialog("Draw line ROIs, press 't' to save to ROI manager. When you have selected all your ROIs press OK").show();
        Roi[] roiArray = RoiManager.getRoiManager().getRoisAsArray();
        return roiArray;
    }

    public boolean makeYesNoDialog(String title, String message) {
        GenericDialog openSavedROIs = new GenericDialog(title);
        openSavedROIs.addMessage(message);
        openSavedROIs.enableYesNoCancel();
        openSavedROIs.hideCancelButton();
        openSavedROIs.showDialog();
        return openSavedROIs.wasOKed();
    }


    private double[] getScaleFromFile(String scaleFile){
        double[] scale = new double[2];
        try{
            BufferedReader bufferedReader= new BufferedReader(new FileReader(scaleFile));
            String line;
            while ((line=bufferedReader.readLine()) != null){
                if(line.contains("Pixel Width: ")){
                    scale[0] = Double.parseDouble(line.substring(13));
                }
                if(line.contains("Frame Interval: ")){
                    scale[1] = Double.parseDouble(line.substring(16));
                }
            }
        }catch(IOException ex) {
            System.out.println("File Not Found");
            return scale;
        }

        return scale;
    }

    private double[] getOldScaleFromFile(String scaleFile){
        double[] scale = new double[2];
        try{
            BufferedReader bufferedReader= new BufferedReader(new FileReader(scaleFile));
            String line;
            while ((line=bufferedReader.readLine()) != null){
                scale[0] = Double.parseDouble(line.substring(0,3))/1000;
                scale[1] = Double.parseDouble(line.substring(4));
                IJ.log("line: "+line.length()+ "  scale 0: "+scale[0]+ " scale 1:" +scale[1]);
            }
        }catch(IOException ex) {
            System.out.println("File Not Found");
            return scale;
        }
        return scale;
    }

    private boolean isOldScaleFormat(String scaleFile){
        boolean isOldFormat = true;
        try{
            BufferedReader bufferedReader= new BufferedReader(new FileReader(scaleFile));
            String line;
            while ((line=bufferedReader.readLine()) != null){
                if(line.contains("File")){
                    isOldFormat=false;
                }
            }
        }catch(IOException ex) {
            IJ.log("Scale File Not Found");
            return isOldFormat;
        }
        IJ.log("Is Old Format :"+ isOldFormat);
        return isOldFormat;
    }

}

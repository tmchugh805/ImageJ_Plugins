import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class AG_Kinetochore_Intensities implements PlugIn{

    String directory;
    String filename;
    boolean[] includeChannel;
    ImagePlus referenceImage;
    String[] fileNameList;
    double[] slice;
    Roi[] roiArray;

    public void run(String s) {

        new WaitForUserDialog("Open Image", "Open Images. SPLIT CHANNELS!").show();
        IJ.run("Bio-Formats Importer");// Open new file
        String[] channelTitles = WindowManager.getImageTitles();
        //Crop Images
        filename = WindowManager.getCurrentImage().getShortTitle();//Count number of open windows (channels)
        fileNameList = channelSelector(channelTitles);
        directory = createNewFolder();

        new WaitForUserDialog("Select", "Select the reference channel").show();
        referenceImage = WindowManager.getCurrentImage();
        IJ.setTool("rectangle");
        new WaitForUserDialog("Select", "Select area with cell to be analysed, press 'T'").show();
        RoiManager roiManager = RoiManager.getRoiManager();
        for (String value : fileNameList) {
            ImagePlus imp = WindowManager.getImage(value);
            imp.setRoi(roiManager.getRoi(roiManager.getCount() - 1));
            IJ.run(imp, "Crop", "");
            IJ.run(imp, "Enhance Contrast", "saturated=0.35");
        }
        for (int i = 0; i < fileNameList.length; i++) {
            ImagePlus imp = WindowManager.getImage(fileNameList[i]);
            String imageFilename = Paths.get(directory, fileNameList[i]).toString();
            IJ.saveAs(imp,"Tiff",imageFilename);
            imp.setTitle(fileNameList[i]);
        }
        IJ.setTool("point");

        while (roiManager.getCount() != 0) {
            roiManager.runCommand("Delete");
        }
        IJ.run("Brightness/Contrast...");
        IJ.run("In [+]", "");
        new WaitForUserDialog("Select Rois and press 'T' to add to ROI manager").show();

        roiArray = roiManager.getRoisAsArray();
        slice = new double[roiArray.length];

        List<double[]> outputList = new ArrayList<>();
        for( int i = 0; i < roiManager.getCount(); i++){
            double[] roiData = new double[4];
            roiManager.select(i);
            Point[] points = roiArray[i].getContainedPoints();
            slice[i] = roiArray[i].getPosition();
            IJ.log(""+ slice[i]);
            roiData[0] = points[0].x;
            roiData[1] = points[0].y;
            double width = 8;
            double height = 8;
            roiData[2]=slice[i];
            referenceImage.setZ((int) slice[i]);
            roiArray[i] = new OvalRoi(roiData[0]-4, roiData[1]-4, width, height);
            roiArray[i].setPosition((int) slice[i]);
        }
        while(roiManager.getCount()!=0){
            roiManager.runCommand("Delete");
        }

        for (String value : fileNameList) {
            Roi[] newRois = new Roi[roiArray.length];
                ImagePlus image = WindowManager.getImage(value);
                WindowManager.setWindow(new ImageWindow(image));
                image.setSlice((int) slice[0]);
                IJ.run("In [+]", "");
                for (int j = 0; j < roiArray.length; j++) {
                    double[] roiData = new double[4];
                    IJ.log("Z-slice: " + slice[j]);
                    image.setSlice((int) slice[j]);
                    image.setRoi(roiArray[j]);
                    new WaitForUserDialog("Correct ROI and press OK").show();
                    if(roiManager.getCount()==0) {
                        newRois[j] = roiArray[j];
                    }else {
                        newRois[j] = roiManager.getRoi(roiManager.getCount() - 1);
                    }
                    outputList.add(roiData);
                    roiData[0] = newRois[j].getStatistics().xCentroid;
                    roiData[1] = newRois[j].getStatistics().yCentroid;
                    roiData[2] = slice[j];
                    roiData[3] = image.getStatistics().mean;

                    while (roiManager.getCount() != 0) {
                        roiManager.runCommand("Delete");
                    }
            }
                for (int i = 0 ; i< newRois.length; i++){
                roiManager.addRoi(newRois[i]);
                }
             String roiFilename = Paths.get(directory, value+".zip").toString();
            roiManager.runCommand("Save", roiFilename);
        }

        while (roiManager.getCount() != 0) {
            roiManager.runCommand("Delete");
        }
        IJ.setTool("point");
        List<double[]>backgroundList = new ArrayList<>();
        createComposite();
        new WaitForUserDialog("Select background Rois on composite image and press 'T' to add to ROI manager").show();
        Roi[] backgroundArray = roiManager.getRoisAsArray();
        for (String value : fileNameList) {
            ImagePlus image = WindowManager.getImage(value);
            WindowManager.setWindow(new ImageWindow(image));
            IJ.run("In [+]", "");
            for (int j = 0; j < backgroundArray.length; j++) {
                double[] backgroundData = new double[4];
                roiManager.select(j);
                Point[] points = backgroundArray[j].getContainedPoints();
                backgroundData[0] = points[0].x;
                backgroundData[1] = points[0].y;
                image.setSlice(backgroundArray[j].getPosition());
                double width = 8;
                double height = 8;
                backgroundData[2] = -1;
                backgroundArray[j] = new OvalRoi(backgroundData[0] - 4, backgroundData[1] - 4, width, height);
                image.setRoi(backgroundArray[j]);
                backgroundData[3] = image.getStatistics().mean;
                backgroundList.add(backgroundData);
            }
            String roiFilename = Paths.get(directory, "background.zip").toString();
            roiManager.runCommand("Save", roiFilename);
        }

        String resultsFileName = makeResultsFile(outputList,backgroundList,"Mean_Intensities");
        String resultsXYZFileName = makeResultsFileXYZ(outputList);

        IJ.log("Files saved in: "+directory);

    }

    private void createComposite(){
        String mergeString = "";
        for (int i = 0; i < fileNameList.length;i++){
            String newString = "c"+(i+1)+"="+fileNameList[i]+" ";
            mergeString=mergeString.concat(newString);
        }
        mergeString= mergeString.concat(" create keep");
        IJ.run(referenceImage, "Merge Channels...", mergeString);
    }

    public boolean makeYesNoDialog(String title, String message) {
        GenericDialog openSavedROIs = new GenericDialog(title);
        openSavedROIs.addMessage(message);
        openSavedROIs.enableYesNoCancel();
        openSavedROIs.hideCancelButton();
        openSavedROIs.showDialog();
        return openSavedROIs.wasOKed();
    }
    private String createNewFolder(){

        boolean makeNewFolder = makeYesNoDialog("New Folder", "Save in current directory?");
        String baseDirectory = new String();
        if(makeNewFolder) {
             baseDirectory = IJ.getDirectory("current");
        }else{
            new WaitForUserDialog("Select directory to save.").show();
            baseDirectory = IJ.getDirectory("");
        }
        GenericDialog channelDialog = new NonBlockingGenericDialog("Filename");
        channelDialog.addStringField("Folder name:", filename);
        channelDialog.showDialog();
        String newFolder = channelDialog.getNextString();
        String newDirectory = baseDirectory+newFolder;
        File tmpDir = new File(newDirectory);
        String filePath = Paths.get(baseDirectory, newFolder).toString();
        //If the folder already exists gives options to overwrite or use directory to select a different folder.
        if (tmpDir.exists()){
            boolean makeNewDirectory = makeYesNoDialog("Folder already exists", "Folder "+ newDirectory+ " already exists and will be overwritten. Make new Folder?");
            if (makeNewDirectory){
                filePath = IJ.getDirectory("");
            }else{
                String[] entries = tmpDir.list();
                for(String s: entries){
                    File currentFile = new File(tmpDir.getPath(),s);
                    currentFile.delete();
                }
            }
        }else {
            new File(newDirectory).mkdir();
        }
        return filePath;
    }

    private List<double[]> separateChannels(int column, List<double[]> outputList){
        List<double[]> separatedChannels = new ArrayList<>();
        int n = roiArray.length;
        for(int i =0; i<fileNameList.length;i++){
            double[] fileColumn = new double[outputList.size()/fileNameList.length];
            for(int j = 0; j< n; j++){
                fileColumn[j]=outputList.get(j + (i*n))[column];
            }
            separatedChannels.add(fileColumn);
        }
        return separatedChannels;
    }

    private double[] getBackgrounds(int column, List<double[]> outputList){
        double[] backgroundMean = new double[fileNameList.length];
        int n = outputList.size()/fileNameList.length;
        for (int k = 0 ; k< fileNameList.length; k++) {
            for (int i = 0; i < n; i++) {
                backgroundMean[k] = backgroundMean[k] + outputList.get(i + (k * n))[column];
            }
            backgroundMean[k] = backgroundMean[k] / n;
        }
        return backgroundMean;
    }

    private String makeResultsFile(List<double[]> outputList, List<double[]> backgroundList, String filename){

        List<double[]> intensities = separateChannels(3,outputList);
        double[] backgrounds = getBackgrounds(3,backgroundList);

        String CreateName = Paths.get( directory , filename+".txt").toString();
        File resultsFile = new File(CreateName);
        int nfiles = fileNameList.length;
        int i = 1;
        while (resultsFile.exists()){
            CreateName= Paths.get( directory , filename+"_"+i+".txt").toString();
            resultsFile = new File(CreateName);
            IJ.log(CreateName);
            i++;
        }
        try{
            FileWriter fileWriter = new FileWriter(CreateName,true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            bufferedWriter.write("File= " + filename);
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            bufferedWriter.write(",Channel, ");
            for (String s : fileNameList) {
                bufferedWriter.write("Absolute:" + s +",");
                bufferedWriter.write("Background:" + s+",");
            }

            for(int k = 0; k < roiArray.length;k++) {
                bufferedWriter.newLine();
                bufferedWriter.write(","+(k+1));
                for(int j = 0;j<nfiles;j++){
                    bufferedWriter.write("," + intensities.get(j)[k]);
                    bufferedWriter.write("," + (intensities.get(j)[k]-backgrounds[j]));
                }
            }
            bufferedWriter.newLine();
            bufferedWriter.write(",Mean:");
            double mean = 0;
            double meanBG = 0;
            for(int j = 0;j<nfiles;j++){
                for(int k = 0; k < intensities.get(0).length;k++) {
                    mean = mean + intensities.get(j)[k];
                    meanBG = meanBG + (intensities.get(j)[k]-backgrounds[j]);
                }
                bufferedWriter.write("," + mean/roiArray.length);
                bufferedWriter.write("," + meanBG/roiArray.length);
                mean=0;
                meanBG=0;
            }
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            bufferedWriter.write("Over");
            bufferedWriter.newLine();
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '" + CreateName + "'");
        }
        return CreateName;
    }

    private String makeResultsFileXYZ(List<double[]> outputList){

        List<double[]> xPostions = separateChannels(0,outputList);
        List<double[]> yPostions = separateChannels(1,outputList);
        List<double[]> zPostions = separateChannels(2,outputList);

        String CreateName = Paths.get( directory , "XYZ_coordinates.txt").toString();
        File resultsFile = new File(CreateName);
        int nfiles = fileNameList.length;
        int i = 1;
        while (resultsFile.exists()){
            CreateName= Paths.get( directory , "XYZ_coordinates_"+i+".txt").toString();
            resultsFile = new File(CreateName);
            IJ.log(CreateName);
            i++;
        }
        try{
            FileWriter fileWriter = new FileWriter(CreateName,true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            bufferedWriter.write("File= " + filename);
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            bufferedWriter.write(",Channel, ");
            for (String s : fileNameList) {
                bufferedWriter.write("X" + s +",");
                bufferedWriter.write("Y" + s+",");
                bufferedWriter.write("Z" + s +",");
            }

            for(int k = 0; k < roiArray.length;k++) {
                bufferedWriter.newLine();
                bufferedWriter.write(","+(k+1));
                for(int j = 0;j<nfiles;j++){
                    bufferedWriter.write("," + xPostions.get(j)[k]);
                    bufferedWriter.write("," + yPostions.get(j)[k]);
                    bufferedWriter.write("," + zPostions.get(j)[k]);
                }
            }
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            bufferedWriter.write("Over");
            bufferedWriter.newLine();
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '" + CreateName + "'");
        }
        return CreateName;
    }


    public String[] channelSelector(String[] filenameArray){
        GenericDialog channelDialog = new NonBlockingGenericDialog("Rename Channels");
        int n = filenameArray.length;
        includeChannel = new boolean[n];
        for (String s : filenameArray) {
            channelDialog.setInsets(5,0,0);
            channelDialog.addStringField(s, " ");
            channelDialog.setInsets(-5,0,5);
            channelDialog.addCheckbox("Include Channel?", true);
        }
        channelDialog.showDialog();
        if (channelDialog.wasCanceled()) {     // dialog cancelled?
            return new String[]{null};
        }
        List<String> filenameString = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            String imageTitle = channelDialog.getNextString();
            includeChannel[j] = channelDialog.getNextBoolean();
            ImagePlus newChannel = WindowManager.getImage(filenameArray[j]);
            IJ.run(newChannel, "Grays", "");
            IJ.run(newChannel, "Enhance Contrast", "saturated=0.15");
            if(includeChannel[j]){
                newChannel.setTitle(imageTitle);
                filenameString.add(imageTitle);
            }
            else {
                newChannel.close();
            }
        }
        String[] outputArray = filenameString.toArray(new String[filenameString.size()]);

        return outputArray;
    }



}
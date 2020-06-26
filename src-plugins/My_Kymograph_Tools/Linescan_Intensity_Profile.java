package My_Kymograph_Tools;

import ij.*;
import ij.gui.*;
import ij.plugin.frame.RoiManager;
import ij.process.*;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import sun.plugin.javascript.navig.Array;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cross_Fader 1.0 Michael Schmid 2014-04-22
 * Takes a two-slice stack and copies part of the second slice into the current one.
 */

public class Linescan_Intensity_Profile implements ExtendedPlugInFilter, DialogListener {

    private static int FLAGS =      //bitwise or of the following flags:
                    DOES_ALL |
                            NO_CHANGES|
                            NO_IMAGE_REQUIRED;//this plugin processes 8-bit, 16-bit, 32-bit gray & 24-bit/pxl RGB
                              //When using preview, the preview image can be kept as a result


    private double threshold;
    private Roi[] roiArray;
    private double min;
    private double max;
    private ImagePlus microtubuleImage;
    private String filename;
    private String directory;
    private RoiManager roiManager;
    private boolean[] includeChannel;
    /**
     * This method is called by ImageJ for initialization.
     *
     * @param arg Unused here. For plugins in a .jar file this argument string can
     *            be specified in the plugins.config file of the .jar archive.
     * @param imp The ImagePlus containing the image (or stack) to process.
     * @return The method returns flags (i.e., a bit mask) specifying the
     * capabilities (supported formats, etc.) and needs of the filter.
     * See PlugInFilter.java and ExtendedPlugInFilter in the ImageJ
     * sources for details.
     */
    public int setup(String arg,ImagePlus imp) {
        return FLAGS;
    }

    /**
     * Ask the user for the parameters. This method of an ExtendedPlugInFilter
     * is called by ImageJ after setup.
     *
     * @param imp     The ImagePlus containing the image (or stack) to process.
     * @param command The ImageJ command (as it appears the menu) that has invoked this filter
     * @param pfr     A reference to the PlugInFilterRunner, needed for preview
     * @return Flags, i.e. a code describing supported formats etc.
     */
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {

        new WaitForUserDialog("Open Image", "Open Images. SPLIT CHANNELS!").show();
        IJ.run("Bio-Formats Importer");// Open new file
        //Select the Microtubule channel and Label other channels
        String[] channelTitles = WindowManager.getImageTitles();
        directory = IJ.getDirectory("current");
        filename = WindowManager.getCurrentImage().getShortTitle();//Count number of open windows (channels)
        String[] fileNameList = channelSelector(channelTitles);
        if (fileNameList[0] == null){
            return DONE;
        }
        new WaitForUserDialog("Select", "Select the reference channel").show();
        microtubuleImage = WindowManager.getCurrentImage();

        ImageStatistics stats = microtubuleImage.getStatistics();
        min = stats.min;
        max = stats.max;
        roiManager = new RoiManager();
        roiArray = makeROIs();
        GenericDialog gd = new GenericDialog(command + "...");
        gd.addSlider("Threshold", min, max, min+max/2);
        gd.addDialogListener(this);
        gd.showDialog();// user input (or reading from macro) happens here

        if (gd.wasCanceled())      // dialog cancelled?
            return DONE;

        if (gd.wasOKed()) {
            IJ.log("GD was Oked");
            IJ.log("threshold: "+threshold);
            IJ.log("Selected Channels:");
            for (int i = 0; i < fileNameList.length; i++){
                if(includeChannel[i]){
                    IJ.log(fileNameList[i]);
                }
            }
            Roi[] roiNewArray = roiManager.getRoisAsArray();

            Roi[] trimmedRoiArray = Arrays.copyOfRange(roiNewArray,0,roiNewArray.length - roiArray.length);
            IJ.log("Number of ROIs: "+ trimmedRoiArray.length);
            profilesToFile(trimmedRoiArray, fileNameList);
        }
        return FLAGS;              // makes the user process the slice
    }

    /**
     * Listener to modifications of the input fields of the dialog.
     * Here the parameters should be read from the input dialog.
     *
     * @param gd The GenericDialog that the input belongs to
     * @param e  The input event
     * @return whether the input is valid and the filter may be run with these parameters
     */
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        threshold = gd.getNextNumber();
        thresholdROIs(threshold, microtubuleImage);
        return !gd.invalidNumber() && threshold >= min && threshold <= max;
    }

    public void run(ImageProcessor ip) {
    }



    private void thresholdROIs( double threshold, ImagePlus microtubuleImage) {
        Roi[] trimmedRoiArray = getThresholdedROIs(threshold, roiArray, microtubuleImage);
        if (roiManager.getCount() != 0) {
            roiManager.runCommand("Delete");
        }

        for (int i =0; i < trimmedRoiArray.length; i++) {
            trimmedRoiArray[i].setStrokeWidth(3);
            if (i%2 ==0){
                trimmedRoiArray[i].setStrokeColor(Color.red);
            }else {
                trimmedRoiArray[i].setStrokeColor(Color.blue);
            }
            roiManager.addRoi(trimmedRoiArray[i]);
        }
        roiManager.runCommand(microtubuleImage, "Show All without labels");
        for (Roi roIs : roiArray) {
            roIs.setStrokeWidth(1);
            roiManager.addRoi(roIs);
        }
    }

    public Roi[] getThresholdedROIs(double threshold, Roi[] roiArray, ImagePlus microtubuleImage) {

        List<Roi> trimmedList = new ArrayList<>();

        for (Roi points : roiArray) {
            //Get the intensity profile along microtubules
            microtubuleImage.setRoi(points);
            ProfilePlot profilePlot = new ProfilePlot(microtubuleImage);
            double[] profile = profilePlot.getProfile();

            //Find the start and end point of the microtubule
            int[] startAndEnd = getStartAndEnds(profile, threshold);
            int nSubROIs = startAndEnd.length / 2;
            //plot the ROI on the microtubule image
            double angle = points.getAngle();
            Point[] point = points.getContainedPoints();
            int pointA;
            int pointB;
            for (int j = 0; j < nSubROIs; j++) {
                pointA = startAndEnd[j];
                pointB = startAndEnd[nSubROIs +j];
                if (angle>0){
                    pointA = point.length -pointA;
                    pointB = point.length -pointB;
                }
                Line trimmedRoi = new Line(point[pointA].x, point[pointA].y,
                        point[pointB].x, point[pointB].y);
                if (trimmedRoi.getRawLength()>5){
                trimmedList.add(trimmedRoi);
                }
            }
        }
        return trimmedList.toArray(new Roi[0]);
    }


    public int[] getStartAndEnds(double[] profile, double threshold) {
        List<Integer> start = new ArrayList<>();
        List<Integer> end = new ArrayList<>();
        boolean isMT = false;
        int j = 1;
        while(j<profile.length){
            if(profile[j] >= threshold && !isMT){
                isMT = true;
                start.add(j-1);
            }
            if (profile[j] < threshold && isMT){
                isMT = false;
                end.add(j);
            }
            j++;
        }
        if (start.size()!=end.size()){
            start.remove(start.size()-1);
        }
        start.addAll(end);
        int [] output = start.stream().mapToInt(Integer::intValue).toArray();
        IJ.log(Arrays.toString(output));
        return output;
    }


    public Roi[] makeROIs() {

        IJ.setTool("line");
        ImagePlus refImage = WindowManager.getImage("Microtubules");
        roiManager.runCommand(refImage, "Show All without labels");
        if (roiManager.getCount() != 0) {
            roiManager.runCommand("Delete");
        }
        new WaitForUserDialog("Draw line ROIs, press 't' to save to ROI manager. When you have selected all your ROIs press OK").show();
        return roiManager.getRoisAsArray();
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
        for (int j = 0; j < n; j++) {
            String imageTitle = channelDialog.getNextString();
            includeChannel[j] = channelDialog.getNextBoolean();
            ImagePlus newChannel = WindowManager.getImage(filenameArray[j]);
            newChannel.setTitle(imageTitle);
            filenameArray[j] = imageTitle;
        }

        return filenameArray;
    }

    public void profilesToFile(Roi[] trimmedRoiArray, String[] fileNameList) {

        String CreateName = Paths.get(directory, filename+"_Results_0.txt").toString();
        File resultsFile = new File(CreateName);

        int n = 1;
        while (resultsFile.exists()) {
            CreateName = Paths.get(directory, filename+"Results_" + n + ".txt").toString();
            resultsFile = new File(CreateName);

            n++;
        }
        IJ.log(CreateName);

        writeLineToFile(CreateName, "Source File Name :"+filename,false);
        writeLineToFile(CreateName, "Threhold: "+threshold, false);
        writeLineToFile(CreateName, "Reference Channel: "+microtubuleImage.getTitle(), false);
        writeLineToFile(CreateName, "Channels:",false);

        for (int i = 0; i < fileNameList.length; i++) {
            if(includeChannel[i]) {
                writeLineToFile(CreateName, fileNameList[i], false);
            }
        }


        for (int i = 0; i < fileNameList.length; i++) {
            if(includeChannel[i]) {
                writeLineToFile(CreateName, "Channel: " + fileNameList[i], true);

                for (Roi points : trimmedRoiArray) {
                    ImagePlus channel = WindowManager.getImage(fileNameList[i]);
                    channel.setRoi(points);
                    ProfilePlot profilePlot = new ProfilePlot(channel);
                    double[] profile = profilePlot.getProfile();
                    writeLineToFile(CreateName, profile);
                }
            }
        }
    }

    public void writeLineToFile(String resultsFile, String outputString, boolean space){

        try{
            FileWriter fileWriter = new FileWriter(resultsFile,true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            if (space){
                bufferedWriter.newLine();
            }
            bufferedWriter.write(outputString);
            bufferedWriter.newLine();
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '" + resultsFile + "'");
        }
    }

    public void writeLineToFile(String resultsFile, double[] profile){

        try{
            FileWriter fileWriter = new FileWriter(resultsFile,true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(Arrays.toString(profile).substring(1,Arrays.toString(profile).length()-1));
            bufferedWriter.newLine();
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '" + resultsFile + "'");
        }
    }
    /**
     * Set the number of calls of the run(ip) method. This information is
     * needed for displaying a progress bar; unused here.
     */
    public void setNPasses(int nPasses) {
    }
}


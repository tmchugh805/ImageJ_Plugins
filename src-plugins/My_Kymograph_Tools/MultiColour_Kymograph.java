package My_Kymograph_Tools;

import com.sun.java.swing.plaf.windows.resources.windows;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.ImageReader;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MultiColour_Kymograph implements PlugIn {

    String filename;

    public void run(String arg) {

    // Close all open windows and clear ROI manager, open images and get filename, frame interval and pixel width

        IJ.run("Close All", "");//Close all open windows

        new WaitForUserDialog("Open Image", "Open Images. SPLIT CHANNELS!").show();
        IJ.run("Bio-Formats Importer");// Open new file
        ImagePlus imp = WindowManager.getCurrentImage(); // initialise imp
        filename = imp.getShortTitle();
        double frameInterval = imp.getCalibration().frameInterval;
        double pixelWidth = imp.getCalibration().pixelWidth;
        RoiManager roiManager = RoiManager.getRoiManager();
        roiManager.runCommand("Select All");
        roiManager.runCommand("Delete");

    //Make a list of window names

        int numberOfChannels = WindowManager.getWindowCount();//Count number of open windows (channels)
        List <String>  filenameList = new ArrayList<>();//Create an empty list of open window names
        int[] Idlist = WindowManager.getIDList();//get a list of Window IDs
        for (int x=0; x<numberOfChannels; x++){ // for each window open add its name to the filename list
            IJ.selectWindow(Idlist[x]);
            imp = WindowManager.getCurrentImage();
            filenameList.add(imp.getTitle());
            IJ.run(imp, "Enhance Contrast", "saturated=0.35");
        }

        String mergedChannel = "Merged";
        if (filenameList.size()==1){
            mergedChannel = filenameList.get(0);
        }else {
            //Open channel selector to choose which colour for which channel;

            new WaitForUserDialog("Choose Channel Colours", "Check which channel you want in which colour");
            String[] choicesArray = channelSelector(filenameList); //Use the channel selector method to get a string to merge channels
            IJ.run(imp, "Merge Channels...", mergeChannels(choicesArray)); //Merge the selected channels

            if (WindowManager.getWindow("Merged") == null) {
                mergedChannel = filenameList.get(0);
            }
        }
            //Open ROIs from saved file or draw new ROIs

        boolean openSavedROIs = makeYesNoDialog("Open saved ROIs?", "Do you want to open a previously saved ROI zip file?");
        //Ask user to open saved ROIs
        Roi[] roiArray = new Roi[1];
        if(openSavedROIs) {
            roiArray = openSavedROISet();
        }
        boolean makeNewROIs = makeYesNoDialog("Make New ROIs", "Do you want to draw new ROIs?");
        //Ask user to draw new ROIs
        if(makeNewROIs) {
            roiArray = makeNewROIs(filenameList);

            //Save the ROIs in current folder or select a new folder

            boolean saveROIFile = makeYesNoDialog("Save ROIs", "Do you want to save these ROIs?");
            if (saveROIFile) {
                roiManager = RoiManager.getRoiManager();
                if (makeYesNoDialog("Save in current folder?", "Do you want to save in the current folder?")) {
                    roiManager.runCommand("Save", IJ.getDirectory("current") + filename + ".zip");
                } else {
                    roiManager.runCommand("Save", IJ.getDirectory("Select a Directory") + filename + ".zip");
                }
                IJ.log("Files Saved As: " + IJ.getDirectory("current") + filename + ".zip");
            }
        }

    //Make ROIs and save in a new folder with the scale file

        String directory = makeKymoGraphs(roiArray,mergedChannel, filename);
        OutputScaleFile(frameInterval,pixelWidth, directory);

    //Close all windows and clear ROI manager

        if(makeYesNoDialog("Close windows?", "Kymographs saved to "+ directory + "\nClose windows and clear ROI Manager?")){
            IJ.run("Close All", "");
            roiManager = RoiManager.getRoiManager();
            roiManager.runCommand("Select All");
            roiManager.runCommand("Delete");
        }
    }



    //Creates a text file called scale.txt in the folder 'directory' with the filename, pixel width and frame interval

    private void OutputScaleFile(double frameInterval, double pixelWidth, String directory){

        String CreateName = Paths.get( directory , "scale.txt").toString();
        IJ.log(CreateName);
        try{
            FileWriter fileWriter = new FileWriter(CreateName,true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.newLine();
            bufferedWriter.write("File= " + filename);
            bufferedWriter.newLine();
            bufferedWriter.write("Frame Interval: " + frameInterval);
            bufferedWriter.newLine();
            bufferedWriter.write("Pixel Width: " + pixelWidth);
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '" + CreateName + "'");
        }
    }


    //Makes a new folder 'newFolder' in the current directory. If this already exists give the option to create
    //a new folder or overwrite existing folder. Creates Kymographs using the roiArray on open image 'filename'
    //and saves these as Kymograph_x.tif files in the new folder. Returns the directory of the saved Kymographs.

    public String makeKymoGraphs(Roi[] roiArray, String filename, String newFolder){

        RoiManager roiManager = RoiManager.getRoiManager();
        roiManager.runCommand("Delete");
        for (Roi points : roiArray) {
            roiManager.addRoi(points);
        }
        //Creates new folder
        String baseDirectory = IJ.getDirectory("current");
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
    //Makes Kymographs from ROIs on the open image 'filename' and saves in the correct folder
        for (int j = 0; j< roiArray.length;j++){
            IJ.selectWindow(filename);
            ImagePlus imp = WindowManager.getCurrentImage();
            roiManager.select(j);
            IJ.run(imp, "Reslice [/]...", "output=1.000 slice_count=1 avoid");
            String kymographNumber = "Kymograph_"+j+".tif";
            IJ.saveAs("Tiff", Paths.get(filePath,kymographNumber).toString());
        }
        return filePath;
    }



    //Opens a saved ROI .zip file and returns as an array of ROIs

    public Roi[] openSavedROISet(){
        OpenDialog openDialog = new OpenDialog("Open ROI Zip File");
        String roiFilename = openDialog.getFileName();
        IJ.open(roiFilename);
        return RoiManager.getRoiManager().getRoisAsArray();
    }



    //Makes a Z projection of the reference Image. Allows the user to draw ROIs and add them to ROI manager then
    // returns an array of ROIs from the ROI manager.

    public Roi[]  makeNewROIs(List <String> filenameList){

        GenericDialog channelDialog = new NonBlockingGenericDialog("Reference Channel Selector");
        String[] filenameArray = filenameList.toArray(new String[0]);
        channelDialog.addChoice("Reference Channel", filenameArray,filenameArray[0]);
        channelDialog.showDialog();
        String referenceChannel =  channelDialog.getNextChoice();

        IJ.selectWindow(referenceChannel);
        ImagePlus imp = WindowManager.getCurrentImage();
        ImagePlus newImp  = ZProjector.run(imp,"max");
        newImp.show();
        IJ.run(newImp, "Enhance Contrast", "saturated=0.35");
        IJ.setTool("line");

        RoiManager roiManager = RoiManager.getRoiManager();
        roiManager.runCommand(imp,"Show All without labels");
        new WaitForUserDialog("Draw line ROIs, press 't' to save to ROI manager. When you have selected all your ROIs press OK").show();
        Roi[] roiArray = RoiManager.getRoiManager().getRoisAsArray();
        newImp.close();
        return roiArray;
    }



    //Makes a Yes/No dialog box with a title and message, returns a boolean (yes -> true).

    public boolean makeYesNoDialog(String title, String message) {
        GenericDialog openSavedROIs = new GenericDialog(title);
        openSavedROIs.addMessage(message);
        openSavedROIs.enableYesNoCancel();
        openSavedROIs.hideCancelButton();
        openSavedROIs.showDialog();
        return openSavedROIs.wasOKed();
    }



    //Takes an array of 7 filename or null '--' strings and returns a string to enable these file channels to be merged.

    public String mergeChannels(String[] choicesArray ){
        String mergeString = "";
        for (int x =0; x<7 ; x++){
            if(!choicesArray[x].equals("--")) {
                String channelString = "c" + (x+1) + "=[" + choicesArray[x] + "] ";
                mergeString = mergeString.concat(channelString);
            }
        }
        mergeString = mergeString.concat( "create keep");
        return mergeString;
    }


    //Takes a list of filenames and creates a user dialog box to enable users to select channel colours and returns a
    // string[7] array of these choices.

    public String[] channelSelector(List <String> filenameList){
        GenericDialog channelDialog = new NonBlockingGenericDialog("Channel Selector");
        int n = filenameList.size();
        filenameList.add("--");
        String[] filenameArray = filenameList.toArray(new String[0]);
        IJ.log(filenameList.toString());
        String[] coloursArray = new String[]{"Red","Green","Blue","Grey","Cyan","Magenta","Yellow"};
        for (int j = 0; j < 7; j++) {
            channelDialog.addChoice(coloursArray[j], filenameArray, filenameArray[n]);
        }
        channelDialog.showDialog();
        String[] choicesArray = new String[7];
        for (int i = 0; i < 7; i++){
            choicesArray[i] =  channelDialog.getNextChoice();
        }
        return choicesArray;
    }
}

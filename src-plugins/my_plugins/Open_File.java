package my_plugins;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.WaitForUserDialog;
import ij.io.ImageReader;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Example ImageJ Plugin that inverts an 8-bit grayscale image.
 * This file is part of the 'imagingbook' support suite. See
 * <a href = "http://imagingbook.com"> http://imagingbook.com</a>
 * for details and additional ImageJ resources.
 *
 * @author W. Burger
 */
public class Open_File implements PlugIn {

String filename;


    public void run(String arg) {


        new WaitForUserDialog("Open Image", "Open Images. SPLIT CHANNELS!").show();
        IJ.run("Bio-Formats Importer");

        ImagePlus imp = WindowManager.getCurrentImage();
        filename = imp.getShortTitle();
        double frameInterval = imp.getCalibration().frameInterval;
        double pixelWidth = imp.getCalibration().pixelWidth;

        int numwin = WindowManager.getWindowCount();
        int[] Idlist = new int[numwin];

        Idlist = WindowManager.getIDList();

        for (int x=0; x<numwin; x++){
            IJ.selectWindow(Idlist[x]);
            imp = WindowManager.getCurrentImage();
            IJ.run(imp, "Enhance Contrast", "saturated=0.35");
        }


    }




}


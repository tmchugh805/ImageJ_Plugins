package my_plugins;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.WaitForUserDialog;
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
public class Demo_Plugin implements PlugInFilter {

	String filename;
	public int setup(String args, ImagePlus image) {
		return DOES_8G;
	}


	public void run(ImageProcessor ip) {

		ImagePlus imp = WindowManager.getCurrentImage();
		filename = imp.getShortTitle();
		double[] output = {5,6,6};
		OutputText(output,"Output???");

		IJ.log("I am alive!");	// testing only
		int w = ip.getWidth();
		int h = ip.getHeight();

		for (int u = 0; u < w; u++) {
			IJ.showProgress(u, w);
			for (int v = 0; v < h; v++) {
				int val = ip.getPixel(u, v);
				ip.putPixel(u, v, 255 - val);
			}
		}
	}

	private void OutputText(double [] MeanInt, String colour){

		String CreateName = IJ.getDirectory("current")+ "MyFile.txt";
		String FILE_NAME = CreateName;
		IJ.log(FILE_NAME);
		try{
			FileWriter fileWriter = new FileWriter(FILE_NAME,true);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

			bufferedWriter.newLine();
			bufferedWriter.newLine();
			bufferedWriter.write(" File= " + filename);
			bufferedWriter.newLine();
			bufferedWriter.newLine();
			int numvals = MeanInt.length;
			for (int z = 0; z<numvals;z++){
				bufferedWriter.write(colour + " Dot = " + z + " Mean Intensity = " + MeanInt[z]);
				bufferedWriter.newLine();
			}
			bufferedWriter.close();

		}
		catch(IOException ex) {
			System.out.println(
					"Error writing to file '"
							+ FILE_NAME + "'");
		}
	}

}

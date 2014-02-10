package jgossit.fit;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;

import com.garmin.fit.FileEncoder;
import com.garmin.fit.csv.CSVReader;

public class FitApplication
{

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			System.out.println("Usage:");
			System.out.println(" GarminActivityModifier.jar [options] inputFilename (outputFilename)\n");
			System.out.println("Options:");
			System.out.println(" timestamp  Changes the timestamp of the activity file so that it can be uploaded to Garmin along with the original");
			System.out.println(" elevation  Corrects sample that are missing elevation data, causing spikes in the elevation chart, a larger Y axis than necessary, and incorrect Min/Max/Gained/Lost Elevation numbers");
			System.out.println(" startup    Fix samples at the start of the activity which can have big fluctuations while starting/issues with GPS sync etc.");
			System.out.println(" midrun     Fix samples mid-run with big fluctuations, perhaps losing GPS under bridges or trees/turning around/temporary stop etc. by using surrounding samples average\n");
			System.out.println(" inputFilename   The .fit source file");
			System.out.println(" outputFilename  The .fit file to create, if not specified the original filename will be suffixed with -modified");
			System.exit(0);
		}
		
		FitMessageListener fitMessageListener = new FitMessageListener();
		String inputFilename = null, outputFilename = null;
		for (int i=0; i<args.length; i++)
		{
			String arg = args[i].toLowerCase().trim();
			if (arg.equals("timestamp"))
				fitMessageListener.changeTimestamp();
			else if (arg.equals("elevation"))
				fitMessageListener.correctElevation();
			else if (arg.equals("startup"))
				fitMessageListener.correctStartupFluctuations();
			else if (arg.equals("midrun"))
				fitMessageListener.correctMidRunFluctuations();
			else if (i == args.length - 2 && arg.endsWith("fit"))
				inputFilename = arg;
			else if (i == args.length - 1 && arg.endsWith("fit"))
				if (inputFilename == null)
					inputFilename = args[i];
				else
					outputFilename = args[i];
		}
		if (!new File(inputFilename).exists())
		{
			System.err.println("Input file '" + inputFilename + "' does not exist");
			System.exit(1);
		}
		if (inputFilename.equals(outputFilename))
		{
			System.err.println("Input and output file must be different");
			System.exit(1);
		}
		if (outputFilename == null)
		{
			outputFilename = inputFilename.substring(0, inputFilename.toLowerCase().lastIndexOf(".fit")) + "-modified.fit";
		}
		
		if (new File(outputFilename).exists())
			new File(outputFilename).delete();
		
		FitDecode fitDecode = new FitDecode(new FileInputStream(inputFilename), fitMessageListener);
		StringBuilder sb = fitDecode.decode();
		
		ByteArrayInputStream bis = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
		FileEncoder encoder = new FileEncoder(new File(outputFilename));
		CSVReader.read(bis, encoder, encoder);
		encoder.close();
	}
}
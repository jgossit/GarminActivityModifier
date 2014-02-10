package jgossit.fit;

import java.io.FileNotFoundException;
import java.io.InputStream;

import com.garmin.fit.Decode;
import com.garmin.fit.MesgDefinitionListener;
import com.garmin.fit.MesgListener;

public class FitDecode
{
	private InputStream inputStream;
	private FitMessageListener fitMessageListener;

	public FitDecode(InputStream inputStream, FitMessageListener fitMessageListener)
	{
		this.inputStream = inputStream;
		this.fitMessageListener = fitMessageListener;
	}
	
	public StringBuilder decode() throws FileNotFoundException
	{
		Decode decode = new Decode();
		
		decode.addListener((MesgDefinitionListener)fitMessageListener);
		decode.addListener((MesgListener)fitMessageListener);
		decode.read(inputStream);
		return fitMessageListener.close();
	}
}
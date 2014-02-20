package jgossit.fit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import com.garmin.fit.Factory;
import com.garmin.fit.Field;
import com.garmin.fit.FieldDefinition;
import com.garmin.fit.Fit;
import com.garmin.fit.Mesg;
import com.garmin.fit.MesgDefinition;
import com.garmin.fit.MesgDefinitionListener;
import com.garmin.fit.MesgListener;

public class FitMessageListener implements MesgListener, MesgDefinitionListener
{
	private final static int NUM_SAMPLES_TO_AGGREGATE = 20;
	private final static double MIN_SPEED_FLUCTUATION_PCT = 88.0;
	private final static double MAX_SPEED_FLUCTUATION_PCT = 112.0;
	private final static int TIME_CREATED_COLUMN = 7;
	private final static int SPEED_COLUMN_SAMPLE_MISSING_ELEVATION = 13;
	private final static int SPEED_COLUMN = 19;
	
	private ArrayList<String> headers = new ArrayList<String>();
	private ArrayList<String> values = new ArrayList<String>();
	private ArrayList<String> messages = new ArrayList<String>();
	private ArrayList<Double> speedValues = new ArrayList<Double>();
	
	private boolean changeTimestamp = false;
	private boolean correctElevation = false;
	private boolean correctStartupFluctuations = false;
	private boolean correctMidRunFluctuations = false;
	private boolean averagedSpeedTenSamples = false;
	
	private boolean correctPreviousSample = false;
	private double speedAfterStartupFluctuations = 0.000;
	private int endOfStartupFluctuations = 0;
	
	public FitMessageListener() {}
	
	public FitMessageListener changeTimestamp()
	{
		changeTimestamp = true;
		return this;
	}
	
	public FitMessageListener correctElevation()
	{
		correctElevation = true;
		return this;
	}
	
	public FitMessageListener correctStartupFluctuations()
	{
		correctStartupFluctuations = true;
		return this;
	}
	
	public FitMessageListener correctMidRunFluctuations()
	{
		correctMidRunFluctuations = true;
		return this;
	}
	
	public FitMessageListener averagedSpeedTenSamples()
	{
		averagedSpeedTenSamples = true;
		return this;
	}
	
	public void onMesgDefinition(MesgDefinition mesgDef)
	{
	      Collection<FieldDefinition> fields = mesgDef.getFields();
	      Iterator<FieldDefinition> fieldsIterator;
	      int headerNum;
	      Mesg mesg = Factory.createMesg(mesgDef.getNum());

	      clear();
	      set("Type", "Definition");
	      set("Local Number", mesgDef.getLocalNum());

	      if (mesg == null)
	         set("Message", "unknown");
	      else
	         set("Message", mesg.getName());

	      headerNum = 0;
	      fieldsIterator = fields.iterator();

	      while (fieldsIterator.hasNext())
	      {
	         FieldDefinition fieldDef = fieldsIterator.next();
	         Field field = Factory.createField(mesgDef.getNum(), fieldDef.getNum());
	         headerNum++;

	         if (field == null)
	            set("Field " + headerNum, "unknown");
	         else
	            set("Field " + headerNum, field.getName());

	         set("Value " + headerNum, fieldDef.getSize() / Fit.baseTypeSizes[fieldDef.getType() & Fit.BASE_TYPE_NUM_MASK]);
	         set("Units " + headerNum, "");
	      }

	      writeln();
	}
	
	public void onMesg(Mesg mesg)
	{
	      Collection<Field> fields = mesg.getFields();
	      Iterator<Field> fieldsIterator;
	      int headerNum;

	      clear();
	      set("Type", "Data");
	      set("Local Number", mesg.getLocalNum());
	      set("Message", mesg.getName());

	      headerNum = 0;
	      fieldsIterator = fields.iterator();

	      while (fieldsIterator.hasNext())
	      {
	         Field field = fieldsIterator.next();
	         int subFieldIndex = mesg.GetActiveSubFieldIndex(field.getNum());

	         headerNum++;
	         
	         String fieldName = field.getName(subFieldIndex);

	         set("Field " + headerNum, fieldName);

	         String fieldValue = field.getStringValue(0, subFieldIndex);

	         if (fieldValue == null)
	        	 fieldValue = "";

	         for (int fieldElement = 1; fieldElement < field.getNumValues(); fieldElement++)
	         {
	        	 fieldValue += "|";

	            String nextValue = field.getStringValue(fieldElement, subFieldIndex);

	            if (nextValue != null)
	            	fieldValue += nextValue;
	         }

	         set("Value " + headerNum, fieldValue);
	         set("Units " + headerNum, field.getUnits(subFieldIndex));
	         
	         if (fieldName.equals("speed") && fieldValue != null)
	        	 speedValues.add(Double.parseDouble(fieldValue));
	      }

	      writeln();
	}
	
	public StringBuilder close()
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < headers.size(); i++)
			sb.append(headers.get(i) + ",");

		messages.add(0, sb.toString());
		
		findEndOfStartupFluctuations();
		correctMidRunFluctuations(true);
		
		int sampleNumber = 0;
		sb = new StringBuilder();
		for(int i=0;i<messages.size();i++)
		{
			String message = messages.get(i);
			if (!message.startsWith("Data,6"))
			{
				sb.append(message + "\n");
			}
			else
			{
				String[] messageSplit = message.split(",");
				// if the sample is missing elevation details and correction is not on there will be less columns
				String originalSpeed = messageSplit.length > SPEED_COLUMN ? messageSplit[SPEED_COLUMN] :
																			messageSplit[SPEED_COLUMN_SAMPLE_MISSING_ELEVATION];
				
				Double speed = averagedSpeedTenSamples(sampleNumber);
				message = message.replaceFirst("speed," + originalSpeed + ",", "speed," + speed + ",");
				
				sb.append(message + "\n");
				sampleNumber++;
			}
		}
		return sb;
	}
	
	/**
	 * Averages the sample speed over the next/previous 10 samples to produce a smoother graph 
	 * @param sampleNumber
	 * @return Double The speed of the current sample averaged with the next/previous 10 samples
	 */
	private Double averagedSpeedTenSamples(int sampleNumber)
	{
		Double totalSpeed = speedValues.get(sampleNumber);
		if (!averagedSpeedTenSamples)
			return totalSpeed;
			
		int numSamples = 1;
		int nextSampleNumber = sampleNumber;
		
		while (numSamples < 10 && nextSampleNumber < speedValues.size() && nextSampleNumber >= 0)
		{
			if (nextSampleNumber >= sampleNumber)
			{
				if (nextSampleNumber+1 < speedValues.size())
					nextSampleNumber++;
				else // reached the end, start using prior samples
					nextSampleNumber = sampleNumber -1;
			}
			else
			{
				nextSampleNumber--;
			}
			totalSpeed += speedValues.get(nextSampleNumber);
			numSamples++;
		}
		return totalSpeed / numSamples;
	}
	
	/**
	 * Samples at the start of an activity can often have big fluctuations when getting start/issues with GPS
	 * sync etc. This attempts to find the first sample after the fluctuations have dispersed and replace all values
	 * beforehand
	 */
	private void findEndOfStartupFluctuations()
	{
		if (!correctStartupFluctuations)
			return;
		
		ArrayList<Double> averageSpeeds = getAverageSpeeds();
		
		int numSamplesSinceFluctuation = 0;
		for (int i=0; i<speedValues.size(); i++)
		{
			Double fluctuationFromAverage = speedValues.get(i) / averageSpeeds.get(i) * 100;
			if (fluctuationFromAverage >= MIN_SPEED_FLUCTUATION_PCT && fluctuationFromAverage <= MAX_SPEED_FLUCTUATION_PCT)
				numSamplesSinceFluctuation++;
			else
				numSamplesSinceFluctuation = 0;
			
			if (numSamplesSinceFluctuation == NUM_SAMPLES_TO_AGGREGATE)
			{
				endOfStartupFluctuations = (i+1) - NUM_SAMPLES_TO_AGGREGATE;
				speedAfterStartupFluctuations = speedValues.get(endOfStartupFluctuations);
				break;
			}
		}
		
		for(int i=0; i<endOfStartupFluctuations; i++) // replace all earlier values with a flat value
			speedValues.set(i, speedAfterStartupFluctuations);
	}
	
	/**
	 * Samples mid-run can also have big fluctuations, when losing GPS under bridge/trees, turning around, a temporary
	 * stop to avoid traffic etc. This attempts to remove the fluctuations by replacing the sample speeds with average
	 * value(s) between the previous and next sensible sample speeds.
	 * @param speedTooHigh Correct speeds that are too high (first pass) or too low (second pass)
	 */
	private void correctMidRunFluctuations(boolean speedTooHigh)
	{
		if (!correctMidRunFluctuations)
			return;
		
		int numCorrections = 0, prevNumCorrections = -1;
		do
		{
			ArrayList<Double> averageSpeeds = getAverageSpeeds();
			if (numCorrections != 0)
				prevNumCorrections = numCorrections;
			numCorrections = 0;
			int concurrentFluctuationFromAverage = 0;
			for (int i=endOfStartupFluctuations+1; i<speedValues.size(); i++)
			{
				Double diffFromAverage = speedValues.get(i) / averageSpeeds.get(i) * 100;
				if ((speedTooHigh && diffFromAverage < MAX_SPEED_FLUCTUATION_PCT) ||
					(!speedTooHigh && diffFromAverage > MIN_SPEED_FLUCTUATION_PCT))
				{
					// got an acceptable value, fix and reset (if necessary)
					if (concurrentFluctuationFromAverage != 0)
					{
						numCorrections++;
						int previousSample = i - concurrentFluctuationFromAverage - 1;
						Double previousSpeed = speedValues.get(previousSample);
						Double nextSpeed = speedValues.get(i);
						Double incrementSpeed = (nextSpeed - previousSpeed) / (concurrentFluctuationFromAverage + 1);
						for (int j=1; j<=concurrentFluctuationFromAverage; j++)
						{
							Double correctedSpeed = previousSpeed + (j * incrementSpeed);
							speedValues.set(previousSample + j, correctedSpeed);
						}
					}
					concurrentFluctuationFromAverage = 0;
					continue;
				}
				concurrentFluctuationFromAverage++;
			}
			if (numCorrections == 0) // all done
				prevNumCorrections = 0;
		}
		while (numCorrections != prevNumCorrections); // don't get stuck
		
		if (speedTooHigh)
			correctMidRunFluctuations(false); // second pass
	}
	
	/**
	 * Calculates an average for each sample using the previous (or a combination of next/previous for the first 20)
	 * 20 sample speeds.
	 * @return ArrayList<Double> The average speeds of samples with their previous/next 20 samples
	 */
	private ArrayList<Double> getAverageSpeeds()
	{
		Double lastTwentySpeedsAgg = 0.0;
		ArrayList<Double> averageSpeeds = new ArrayList<Double>();
		for (int i=0; i<speedValues.size(); i++)
		{
			lastTwentySpeedsAgg += speedValues.get(i);
			if (i < NUM_SAMPLES_TO_AGGREGATE)
				continue;
			
			if (i == NUM_SAMPLES_TO_AGGREGATE) // use the same average for the first 20 samples
			{
				for (int j=0; j<NUM_SAMPLES_TO_AGGREGATE; j++)
					averageSpeeds.add(lastTwentySpeedsAgg / NUM_SAMPLES_TO_AGGREGATE);
			}
			
			lastTwentySpeedsAgg -= speedValues.get(i-NUM_SAMPLES_TO_AGGREGATE); // remove the earliest sample
			averageSpeeds.add(lastTwentySpeedsAgg / NUM_SAMPLES_TO_AGGREGATE);
		}
		return averageSpeeds;
	}
	
	public void clear()
	{
	      for (int i = 0; i < values.size(); i++)
	         values.set(i, new String(""));
	}
	
	public void set(String header, Object value)
	{
	      if (header == null)
	         header = "null";

	      if (value == null)
	         value = "null";
	      
	      for (int i = 0; i < headers.size(); i++)
	      {
	         if (headers.get(i).compareTo(header) == 0)
	         {
	            values.set(i, value.toString());
	            return;
	         }
	      }

	      headers.add(header.toString());
	      values.add(value.toString());
	}

	public void writeln()
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < values.size(); i++)
			sb.append(values.get(i) + ",");
		
		String message = sb.toString();
		 // bad entries, skip
		if (message.contains("unknown"))
			return;
		else if (message.startsWith("Data,6") && message.contains("start_position_long"))
			return;
		
		message = changeTimestamp(message);
		message = correctElevation(message);
		messages.add(message);
	}
	
	/**
	 * Changes the timestamp of the activity file so that it can be uploaded to Garmin along with the original,
	 * otherwise an 'activity already exists' will result
	 * @param message The FIT line string
	 * @return String The FIT line string with the activity time_created value incremented by 1
	 */
	private String changeTimestamp(String message)
	{
		if (!changeTimestamp)
			return message;
		
		if (!message.startsWith("Data,6") && message.contains("garmin_product") && message.contains("time_created"))
		{
			String[] messageSplit = message.split(",");
			int timeCreated = Integer.parseInt(messageSplit[TIME_CREATED_COLUMN]);
			message = message.replaceFirst("time_created," + timeCreated + ",", "time_created," + (timeCreated+2) + ",");
		}
		return message;
	}
	
	/**
	 * Corrects sample that are missing elevation data, causing spikes in the elevation chart, a larger Y axis than
	 * necessary, and incorrect Min/Max/Gained/Lost Elevation numbers
	 * 
	 * @param message The FIT line string
	 * @return String The FIT line string with (potentially) missing elevation information inserted
	 */
	private String correctElevation(String message)
	{
		if (!correctElevation)
			return message;
		
		// no elevation data recorded, use last good sample
		if (message.startsWith("Data,6") && message.contains("distance") && !message.contains("semicircles"))
		{
			int prevMessageIndex = messages.size() - 1;
			while (prevMessageIndex >= 0)
			{
				String previousSample = messages.get(prevMessageIndex);
				if (previousSample.startsWith("Data,6") && message.contains("distance") && message.contains("semicircles"))
				{
					int beginIndex = previousSample.indexOf("position_lat");
					int endIndex = previousSample.indexOf("distance");
					String previousDistance = previousSample.substring(beginIndex, endIndex);
					message = message.replace("distance", previousDistance + "distance");
					break;
				}
				prevMessageIndex--;
			}
			if (prevMessageIndex < 0) // first sample is bad, fix it up when we get to the next sample
				correctPreviousSample = true;
		}
		
		if (correctPreviousSample && message.startsWith("Data,6") && message.contains("distance") && message.contains("semicircles"))
		{
			int sampleNumber = messages.size() - 1;
			String previousSample = messages.get(sampleNumber);
			while (sampleNumber >= 0)
			{
				if (previousSample.startsWith("Data,6") && previousSample.contains("distance") && !previousSample.contains("semicircles"))
				{
					int beginIndex = message.indexOf("position_lat");
					int endIndex = message.indexOf("distance");
					String thisDistance = message.substring(beginIndex, endIndex);
					messages.set(sampleNumber, previousSample.replace("distance", thisDistance + "distance"));
					correctPreviousSample = false;
					break;
				}
				sampleNumber--;
				previousSample = messages.get(sampleNumber);
			}
		}

		return message;
	}
}
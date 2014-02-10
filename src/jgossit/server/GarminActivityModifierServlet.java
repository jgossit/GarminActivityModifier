package jgossit.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jgossit.fit.FitDecode;
import jgossit.fit.FitMessageListener;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.garmin.fit.ByteArrayEncoder;
import com.garmin.fit.csv.CSVReader;

public class GarminActivityModifierServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;
	private static final String REDIRECT_PAGE = "garminactivitymodifier.html";
	private static final String ACCEPT_EXTENSION = ".fit";
	
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException
	{
		String redirectUrl = req.getRequestURI().replaceFirst("garminactivitymodifier.*", REDIRECT_PAGE);

		boolean isMultipart = ServletFileUpload.isMultipartContent(req);
		if (!isMultipart)
		{
			resp.sendRedirect(redirectUrl);
			return;
		}
		
		String filename = null;
		List<String> options = new ArrayList<String>();
		ServletFileUpload upload = new ServletFileUpload();
		try
		{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			FileItemIterator iterator = upload.getItemIterator(req);
			while (iterator.hasNext())
			{
				FileItemStream item = iterator.next();
				if (!item.isFormField()) // file input
				{
					if (!item.getName().toLowerCase().endsWith(ACCEPT_EXTENSION))
					{
						resp.sendRedirect(redirectUrl + "?message=" + URLEncoder.encode("Invalid Activity file extension, only .fit files are accepted","ASCII"));
						return;
					}
					filename = item.getName();
					int extensionIndex = filename.lastIndexOf(".");
					filename = filename.substring(0,extensionIndex) + "-modified" + filename.substring(extensionIndex); 
					
					InputStream fileInputStream = item.openStream();
					int b = -1;
					while ((b = fileInputStream.read()) != -1)
					{
						bos.write(b);
					}
					bos.flush();
				}
				else
				{
					options.add(item.getFieldName());
				}
			}
			
			FitMessageListener fitMessageListener = new FitMessageListener();
			if (options.contains("changeTimestamp"))
				fitMessageListener.changeTimestamp();
			if (options.contains("correctElevation"))
				fitMessageListener.correctElevation();
			if (options.contains("correctStartupFluctuations"))
				fitMessageListener.correctStartupFluctuations();
			if (options.contains("correctMidRunFluctuations"))
				fitMessageListener.correctMidRunFluctuations();
			
			FitDecode fitDecode = new FitDecode(new ByteArrayInputStream(bos.toByteArray()), fitMessageListener);
			StringBuilder sb = fitDecode.decode();
			ByteArrayInputStream bis = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
			bos = new ByteArrayOutputStream();
			ByteArrayEncoder encoder = new ByteArrayEncoder(bos);
			CSVReader.read(bis, encoder, encoder);
			encoder.close();

	        resp.setContentType("application/octet-stream; charset=UTF-8");
	        resp.addHeader("content-disposition", "inline;filename=" + filename);
	        resp.getOutputStream().write(bos.toByteArray());
		}
		catch (FileUploadException e)
		{
			resp.sendRedirect(redirectUrl + "?message=" + URLEncoder.encode("Error in file upload - " + e.getMessage(),"ASCII"));
			return;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			resp.sendRedirect(redirectUrl + "?message=" + URLEncoder.encode("Error modifying fit activity file - " + e.getMessage(),"ASCII"));
			return;
		}
	}
	
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
	{
		doPost(req, resp);
	}
}
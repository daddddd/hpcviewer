package edu.rice.cs.hpc.data.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * 
 * grep class
 *
 */
public class Grep 
{
	// Charset and decoder for ISO-8859-15
	private static Charset charset = Charset.forName("ISO-8859-15");
	private static CharsetDecoder decoder = charset.newDecoder();

	// Pattern used to parse lines
	final private static Pattern linePattern	= Pattern.compile(".*\r?\n");


	/**
	 * 
	 * @param fin
	 * @param fout
	 * @param pattern
	 * @param shouldMatch
	 */
	static public void grep(String fin, String fout, String pattern, boolean shouldMatch)
	{
		try {
			if (false) {
				// using generic grep to remove metrics
				File file_in = new File(fin);
				File file_out = new File(fout);
				 	
				grep(file_in, file_out, pattern, shouldMatch);
			} else {
				// using specific grep to remove metric tag
				removeMetrics(fin, fout);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	/**
	 * Use the linePattern to break the given CharBuffer into lines, applying
	 * the input pattern to each line to see if we have a match
	 * 
	 * @param cbin : input buffer
	 * @param cbout : output buffer
	 * @param pattern
	 * @throws IOException 
	 */
	private static void grep(CharBuffer cbin, BufferedWriter writer, Pattern pattern, boolean isMatched)
			throws IOException 
			{
		Matcher lm = linePattern.matcher(cbin);	// Line matcher
		Matcher pm = null;			// Pattern matcher
		while (lm.find()) {
			CharSequence cs = lm.group(); 	// The current line
			if (pm == null)
				pm = pattern.matcher(cs);
			else
				pm.reset(cs);
			
			final boolean found = pm.find();
			if ( (found && isMatched) || (!found && !isMatched ) )
			{
				writer.write(cs.toString());
			}

			if (lm.end() == cbin.limit())
				break;
		}
	}
	
	
	/***
	 * Simplified grep specifically against metric tags in an xml file
	 * This method is not a generic grep, it assumes that all tags are fit in one line
	 * 
	 * @param fin
	 * @param fout
	 * @throws IOException
	 */
	private static void removeMetrics(String fin, String fout) 
			throws IOException 
	{
		//String encoding = "UTF-8";
		
		final BufferedReader inputFile = new BufferedReader(new InputStreamReader(new FileInputStream(fin)));
		final BufferedWriter outputFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fout))); 
		
		Pattern metricTag = Pattern.compile("<M ");
		Matcher matcher = metricTag.matcher("");
		
		String inputLine;
		while ((inputLine = inputFile.readLine()) != null) {
			matcher.reset(inputLine);
	        if (!matcher.lookingAt()) {
	        	outputFile.write(inputLine);
	        	outputFile.newLine();
	        }
		}
		inputFile.close();
		outputFile.close();
	}

	/***
	 * Search for occurrences of the input pattern in the given file
	 * 
	 * @param fin
	 * @param fout
	 * @param pattern
	 * @param isMatched : whether we want to match or not to match
	 * @throws IOException
	 */
	private static void grep(File fin, File fout, String pattern, boolean isMatched) throws IOException {

		// Open the file and then get a channel from the stream
		FileInputStream fis = new FileInputStream(fin);
		FileChannel fic = fis.getChannel();

		final FileWriter fos = new FileWriter(fout);
		final BufferedWriter bwos = new BufferedWriter(fos);

		// Get the file's size and then map it into memory
		int sz = (int)fic.size();
		MappedByteBuffer bbin = fic.map(FileChannel.MapMode.READ_ONLY, 0, sz);

		// we don't need to decode unicode stuffs. We assume the content is just right.
		// Decode the file into a char buffer
		CharBuffer cbin = decoder.decode(bbin);

		// Perform the search
		grep(cbin, bwos, Pattern.compile(pattern), isMatched);

		// Close the channel and the stream
		fic.close();
		bwos.flush();
		bwos.close();
	}

	/**
	 * for unit test only
	 * @param Argvs
	 */
	static public void main(String args[])
	{
		if (args.length>1)
		{
			final String file = args[0];
			final String fout = args[1];
			try {
				Grep.removeMetrics(file, fout);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//Grep.grep(file, fout, "<M ", false);
		}
	}
}

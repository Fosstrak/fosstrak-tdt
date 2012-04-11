/*
 * Copyright (C) 2007 University of Cambridge
 *
 * This file is part of Fosstrak (www.fosstrak.org).
 *
 * Fosstrak is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 2.1, as published by the Free Software Foundation.
 *
 * Fosstrak is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Fosstrak; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package org.fosstrak.tdt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.epcglobalinc.tdt.EpcTagDataTranslation;
import org.epcglobalinc.tdt.Field;
import org.epcglobalinc.tdt.GEPC64;
import org.epcglobalinc.tdt.GEPC64Entry;
import org.epcglobalinc.tdt.Level;
import org.epcglobalinc.tdt.LevelTypeList;
import org.epcglobalinc.tdt.ModeList;
import org.epcglobalinc.tdt.Option;
import org.epcglobalinc.tdt.PadDirectionList;
import org.epcglobalinc.tdt.Rule;
import org.epcglobalinc.tdt.Scheme;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * 
 * This class provides methods for translating an electronic product code (EPC)
 * between various levels of representation including BINARY, TAG_ENCODING,
 * PURE_IDENTITY and LEGACY formats. An additional output level ONS_HOSTNAME may
 * be defined for some coding schemes.
 * 
 * @author Mark Harrison [University of Cambridge] - mark.harrison@cantab.net
 * @author James Brusey
 * @author Jochen Mader - jochen@pramari.com
 * @author Christian Floerkemeier
 */
public class TDTEngine {

	// --------------------------/
	// - Class/Member Variables -/
	// --------------------------/

	final Boolean showdebug=true; // if true, print any debug messages;

	
	/**
	 * prefix_tree_map is a map of levels to prefix trees. Each prefix tree is a
	 * Trie structure (see wikipedia) that is useful for quickly finding a
	 * matching prefix.
	 */
	private Map<LevelTypeList, PrefixTree<PrefixMatch>> prefix_tree_map = new HashMap<LevelTypeList, PrefixTree<PrefixMatch>>();

	/**
	 * HashMap gs1cpi is an associative array providing a lookup between either
	 * a GS1 Company Prefix and the corresponding integer-based Company Prefix
	 * Index, where one of these has been registered for use with 64-bit EPCs -
	 * or the reverse lookup from Company Prefix Index to GS1 Company Prefix.
	 * Note that this is an optimization to avoid having to do an xpath trawl
	 * through the CPI table each time.
	 */
	private HashMap<String, String> gs1cpi = new HashMap<String, String>();

	/** The gepc64 table xml. */
	private String GEPC64xml;

	// ----------------/
	// - Constructors -/
	// ----------------/

	/**
	 * Legacy constructor for a new Tag Data Translation engine.
	 * 
	 * @param confdir
	 *            the string value of the path to a configuration directory
	 *            consisting of two subdirectories, <code>schemes</code> and
	 *            <code>auxiliary</code>.
	 * 
	 *            <p>
	 *            When the class TDTEngine is constructed, the path to a local
	 *            directory must be specified, by passing it as a single string
	 *            parameter to the constructor method (without any trailing
	 *            slash or file separator). e.g.
	 *            <code><pre>TDTEngine engine = new TDTEngine("/opt/TDT");</pre></code>
	 *            </p>
	 *            <p>
	 *            The specified directory must contain two subdirectories named
	 *            auxiliary and schemes. The Tag Data Translation definition
	 *            files for the various coding schemes should be located inside
	 *            the subdirectory called <code>schemes</code>. Any auxiliary
	 *            lookup files (such as <code>ManagerTranslation.xml</code>)
	 *            should be located inside the subdirectory called
	 *            <code>auxiliary</code>.
	 * 
	 *            Files within the schemes directory ending in <code>.xml</code>
	 *            are read in and unmarshalled using JAXB.
	 */
	@Deprecated
	public TDTEngine(String confdir) throws FileNotFoundException,
			MarshalException, ValidationException, TDTException {

		
		try {
			Unmarshaller unmar = getUnmarshaller();
			URL confdirurl;
			if (confdir.endsWith("/")) {
				confdirurl = new URL("file","localhost",confdir);
			} else {
				confdirurl = new URL("file","localhost",confdir+"/");
			}

			URL scheme = new URL(confdirurl,"schemes/");
			URL auxGEPC64table = new URL(confdirurl,"auxiliary/ManagerTranslation.xml");

			Set<String> schemes = new HashSet<String>();

			URLConnection urlcon = scheme.openConnection();
			urlcon.connect();
			BufferedReader in = new BufferedReader(new InputStreamReader(urlcon.getInputStream()));
			String line;
			for (; (line = in.readLine()) != null;) {
				if (line.endsWith(".xml")) {
					URL defurl = new URL(scheme,line);
					loadEpcTagDataTranslation(unmar, defurl);
					schemes.add(line);
				}
			}
			loadGEPC64Table(unmar, auxGEPC64table);
		} catch (MalformedURLException e) {
			throw new FileNotFoundException(e.getMessage());
		} catch (IOException e) {
			throw new FileNotFoundException(e.getMessage());
		} catch (JAXBException e) {
			throw new MarshalException(e);
		}

	}

	/**
	 * Constructor for a new Tag Data Translation engine. This constructor uses
	 * the schemes included on the classpath in a directory called schemes 
	 * (or from within the jar). The ManagerTranslation.xml file is loaded from a directory
	 * called auxiliary on the classpath. All schemes must have filenames ending in .xml
	 * Note that previously this constructor required all schemes to be listed within a file schemes.list
	 * The constructor has now been rewritten to remove this constraint.
	 * Instead, the engine will attempt to load all .xml files within the schemes/ directory.
	 * 
	 * @throws IOException
	 *             thrown if the url is unreachable
	 * @throws JAXBException
	 *             thrown if the schemes could not be parsed
	 */
	public TDTEngine() throws IOException, JAXBException {
				
		Unmarshaller unmar = getUnmarshaller();

		URL auxiliary = this.getClass().getClassLoader().getResource("auxiliary/ManagerTranslation.xml");

		URL schemesdir = this.getClass().getClassLoader().getResource("schemes/");

        String inputSchemeLine;
		
		try {
		URL parent = new URL(schemesdir,".");
		URLConnection urlcon = schemesdir.openConnection();
		BufferedReader dis = new BufferedReader(new InputStreamReader(urlcon.getInputStream()));
		
		while ((inputSchemeLine = dis.readLine()) != null) {
			if (inputSchemeLine.endsWith(".xml")) {
				URL schemeURL = new URL(schemesdir, inputSchemeLine);
				loadEpcTagDataTranslation(unmar, schemeURL);				
			}
		}
		dis.close();
        } catch (MalformedURLException me) {
            System.out.println("MalformedURLException: " + me);
        } catch (IOException ioe) {
            System.out.println("IOException: " + ioe);
        }
				
		loadGEPC64Table(unmar, auxiliary);
	}

	/**
	 * Constructor for a new Tag Data Translation engine. All files are
	 * unmarshalled using JAXB.
	 * 
	 * @param auxiliary
	 *            URL to the auxiliary file containing a GEPC64Table
	 * @param schemes
	 *            directory containing the schemes, all files ending in xml are
	 *            read and parsed
	 * @throws IOException
	 *             thrown if the url is unreachable
	 * @throws JAXBException
	 *             thrown if the files could not be parsed
	 */
	public TDTEngine(URL auxiliary, URL schemes) throws IOException,
			JAXBException {
		Unmarshaller unmar = getUnmarshaller();
		URLConnection urlcon = schemes.openConnection();
		urlcon.connect();
		BufferedReader in = new BufferedReader(new InputStreamReader(urlcon
				.getInputStream()));
		String line;
		for (; (line = in.readLine()) != null;) {
			if (line.endsWith(".xml")) {
				loadEpcTagDataTranslation(unmar, new URL(schemes.toString()
						+ line));
			}
		}
		loadGEPC64Table(unmar, auxiliary);
	}

	/**
	 * Constructor for a new Tag Data Translation engine. All files are
	 * unmarshalled using JAXB.
	 * 
	 * @param auxiliary
	 *            URL to the auxiliary file containing a GEPC64Table
	 * @param schemes
	 *            set containing several urls pointing to directories containing
	 *            the schemes. All files ending in xml are read and parsed.
	 * @param absolute
	 *            true if the given URLs are absolute
	 * @throws IOException
	 *             thrown if the url is unreachable
	 * @throws JAXBException
	 *             thrown if the files could not be parsed
	 */
	public TDTEngine(URL auxiliary, Set<URL> schemes, boolean absolute)
			throws JAXBException, IOException {
		Unmarshaller unmar = getUnmarshaller();
		for (URL scheme : schemes) {
			if (absolute) {
				loadEpcTagDataTranslation(unmar, scheme);
				continue;
			}

			URLConnection urlcon = scheme.openConnection();
			urlcon.connect();
			BufferedReader in = new BufferedReader(new InputStreamReader(urlcon
					.getInputStream()));
			String line;
			for (; (line = in.readLine()) != null;) {
				if (line.endsWith(".xml")) {
					loadEpcTagDataTranslation(unmar, new URL(schemes.toString()
							+ line));
				}
			}
		}
		loadGEPC64Table(unmar, auxiliary);
	}

	/**
	 * Creates the unmarshaller.
	 * 
	 * @return
	 * @throws JAXBException
	 */
	private Unmarshaller getUnmarshaller() throws JAXBException {
		JAXBContext context = JAXBContext.newInstance(
				EpcTagDataTranslation.class, GEPC64.class, GEPC64Entry.class);
		return context.createUnmarshaller();
	}

	/**
	 * Load an xml file from the given url and unmarshal it into an
	 * EPCTagDataTranslation.
	 * 
	 * @param unmar
	 * @param schemeUrl
	 * @throws IOException
	 * @throws JAXBException
	 */
	private void loadEpcTagDataTranslation(Unmarshaller unmar, URL schemeUrl)
			throws IOException, JAXBException {
		URLConnection urlcon = schemeUrl.openConnection();
		urlcon.connect();
		// xml doesn't have enough info for jaxb to figure out
		// the
		// classname, so we are doing explicit loading
		JAXBElement<EpcTagDataTranslation> el = unmar.unmarshal(
				new StreamSource(urlcon.getInputStream()),
				EpcTagDataTranslation.class);
		EpcTagDataTranslation tdt = el.getValue();
		initFromTDT(tdt);
	}

	/**
	 * Load an xml file from the given url and unmarshal it into a GEPC64Table.
	 * 
	 * @param unmar
	 * @param auxiliary
	 * @throws IOException
	 * @throws JAXBException
	 */
	private void loadGEPC64Table(Unmarshaller unmar, URL auxiliary)
			throws IOException, JAXBException {
		URLConnection urlcon = auxiliary.openConnection();
		urlcon.connect();
		// load the GEPC64Table
		JAXBElement<GEPC64> el = unmar.unmarshal(new StreamSource(urlcon
				.getInputStream()), GEPC64.class);
		GEPC64 cpilookup = el.getValue();
		for (GEPC64Entry entry : cpilookup.getEntry()) {
			String comp = entry.getCompanyPrefix();
			String indx = entry.getIndex().toString();
			gs1cpi.put(indx, comp);
			gs1cpi.put(comp, indx);
		}
	}

		/**
	 * Private method for obtaining a hashmap of URLs for named auxiliary files, whether in a file directory, web directory or web page
	 * 
	 * @param auxdirURL
	 *            URL to the directory containing auxiliary files or web page linking to auxiliary files
	 * @param requiredauxfiles
	 *            Set of individual auxiliary files to be retrieved 
	 * @return a hash map relating the name of the filename and its URL
	 *
	 * @throws IOException
	 *             thrown if the url is unreachable
	 */
    private static HashMap<String,URL> getauxiliaryURLs(URL auxdirURL, Set<String> requiredauxfiles) {
		
		
		HashMap<String, URL> foundauxfiles = new HashMap<String, URL>();
		
		String inputAuxLine;
		
		try {
 			URL parent = new URL(auxdirURL,".");
						
			String relauxiliary=auxdirURL.getFile();
						
            URLConnection urlconauxiliary = auxdirURL.openConnection();
            BufferedReader dis2 = new BufferedReader(new InputStreamReader(urlconauxiliary.getInputStream()));
			
            while ((inputAuxLine = dis2.readLine()) != null) {
				
				for (String requestedfile: requiredauxfiles) {
					if (requestedfile.endsWith(".xml")) {
						String pattern = requestedfile.replaceAll("\\.","\\\\.").replaceAll("^","(").replaceAll("$",")").toString();
						Pattern regex = Pattern.compile(pattern);
						
						
						String pattern2 = requestedfile.replaceAll("\\.","\\\\.").replaceAll("^","href=['\"]([^ ]+?").replaceAll("$",")['\"]").toString();
						Pattern regex2 = Pattern.compile(pattern2);
						
						// check if line includes filename.xml - if so, extract auxiliaryfile           
						Matcher matcher2 = regex.matcher(inputAuxLine);
						if (matcher2.find()) {
							URL relURL = new URL(parent, matcher2.group(1));
							foundauxfiles.put(requestedfile, relURL);
						}
						
						// check if line includes href="filename.xml - if so, extract auxiliaryfile           
						Matcher matcher3 = regex2.matcher(inputAuxLine);
						if (matcher3.find()) {
							URL relURL = new URL(parent, matcher3.group(1));
							foundauxfiles.put(requestedfile, relURL);
						}
					}
				}	
			}
			
			dis2.close();
			
			
        } catch (MalformedURLException me) {
            System.out.println("MalformedURLException: " + me);
        } catch (IOException ioe) {
            System.out.println("IOException: " + ioe);
        }
		return foundauxfiles;    
		
    }
    
	/**
	 * Private method for obtaining a set of URLs for TDT definition files, whether in a file directory, web directory or web page
	 * 
	 * @param schemesdirURL
	 *            URL to the directory containing TDT definition files files or web page linking to TDT definition files
	 * @return a list of URLs of TDT definition files contained within the directory or web page pointed to by the URL
	 *
	 * @throws IOException
	 *             thrown if the url is unreachable
	 */
    private static Set<URL> getschemeURLs(URL schemesdirURL, Set<String> schemes) {
        String inputSchemeLine;

		Set<URL> schemeURLs = new HashSet<URL>();
		try {
 			URL parent = new URL(schemesdirURL,".");

			String relschemes=schemesdirURL.getFile();


			
            URLConnection urlconschemes = schemesdirURL.openConnection();
            BufferedReader dis = new BufferedReader(new InputStreamReader(urlconschemes.getInputStream()));
			
            while ((inputSchemeLine = dis.readLine()) != null) {
				// check if line includes filename.xml - if so, add to Set            
				Matcher matcher = Pattern.compile("([A-Za-z0-9-_]+-[0-9*]+\\.xml)").matcher(inputSchemeLine);
				if (matcher.find()) {
					URL relURL = new URL(parent, matcher.group(0));
					if (!matcher.group(0).contains("test")) {
					schemeURLs.add(relURL);
					schemes.add(matcher.group(0));
					}

 				}
			}
            dis.close();
        } catch (MalformedURLException me) {
            System.out.println("MalformedURLException: " + me);
        } catch (IOException ioe) {
            System.out.println("IOException: " + ioe);
        }
		
		return schemeURLs;
    }




	// -----------/
	// - Methods -/
	// -----------/

	private class PrefixMatch {
		private Scheme s;
		private Level level;

		public PrefixMatch(Scheme s, Level level) {
			this.s = s;
			this.level = level;
		}

		public Scheme getScheme() {
			return s;
		}

		public Level getLevel() {
			return level;
		}
	}

	private class PrefixMatch2 {
		private Scheme s;
		private Level level;
		private String taglength;

		public PrefixMatch2(Scheme s, Level level, String taglength) {
			this.s = s;
			this.level = level;
			this.taglength=taglength;
		}

		public Scheme getScheme() {
			return s;
		}

		public Level getLevel() {
			return level;
		}
		
		public String getTaglength() {
			return taglength;
		}
	}

	/** initialise various indices */
	private void initFromTDT(EpcTagDataTranslation tdt) {
		for (Scheme ss : tdt.getScheme()) {
			// create an index so that we can find a scheme based on tag length

			for (Level level : ss.getLevel()) {
				String s = level.getPrefixMatch();
				if (s != null) {
					// insert into prefix tree according to level type.
					PrefixTree<PrefixMatch> prefix_tree = prefix_tree_map
							.get(level.getType());
					if (prefix_tree == null) {
						prefix_tree = new PrefixTree<PrefixMatch>();
						prefix_tree_map.put(level.getType(), prefix_tree);
					}
					prefix_tree.insert(s, new PrefixMatch(ss, level));
					debugprintln("Insert into prefix_tree Prefix: "+s+" : Scheme="+ss.getName()+" ; TagLength="+level.getType());
				}
			}

		}
	}

	/**
	 * Given an input string, and optionally a tag length, find a scheme / level
	 * with a matching prefix and tag length.
	 */
	private PrefixMatch2 findPrefixMatch(String input, String strTagLength) {

		debugprintln("PrefixMatch with 2 parameters: specified strTagLength = "+strTagLength);
		debugprintln("input was: "+input);
		
		int realTagLength=Integer.parseInt(strTagLength);
		
		List<PrefixMatch> match_list = new ArrayList<PrefixMatch>();
		List<PrefixMatch> alt_match_list = new ArrayList<PrefixMatch>();

		for (PrefixTree<PrefixMatch> tree : prefix_tree_map.values()) {

			List<PrefixMatch> list = tree.search(input);

			if (!list.isEmpty()) {
			
						debugprintln("list is not empty (line 566)");
			
				if (strTagLength == null)
					match_list.addAll(list);
				else {

					for (PrefixMatch match : list) {
						BigInteger tagLength = new BigInteger(strTagLength);
						BigInteger schemeTagLength = match.getScheme().getTagLength();
						if (tagLength.compareTo(schemeTagLength) == 0) {
							match_list.add(match);
						debugprintln("Added to match_list");
						debugprintln("Matched scheme :"+match.getScheme().getName().toString());
						debugprintln("scheme taglength = "+ schemeTagLength);
						debugprintln("tagLength = "+tagLength);
						realTagLength=Integer.parseInt(schemeTagLength.toString());
						} else {
							// *** NEW! - Even if tagLength was wrongly specified in extraparams, we still find a match and reset tagLength
							// need TO DO the same for findPrefixMatch with 3 parameters (method below)
							alt_match_list.add(match);
							realTagLength=Integer.parseInt(schemeTagLength.toString());
						debugprintln("Added to alt_match_list");
						debugprintln("Matched scheme :"+match.getScheme().getName().toString());
						debugprintln("scheme taglength = "+ schemeTagLength);
						debugprintln("tagLength = "+tagLength);
						debugprintln("realTagLength = "+realTagLength);
						}
					}
				}
			}
		}

		if (match_list.isEmpty()) {
			debugprintln("match_list was empty");
			if (alt_match_list.size() == 1) {
				debugprintln("but alt_match_list has one element: "+alt_match_list.get(0).getScheme().getName());
				match_list.add(alt_match_list.get(0));
			}
		}


		if (match_list.isEmpty()) {
			debugprintln("***EXCEPTION: No schemes or levels matched the input value (line 608)");
			throw new TDTException("No schemes or levels matched the input value");
		} else if (match_list.size() > 1) {
			debugprintln("***EXCEPTION: More than one scheme/level matched the input value (line 611)");
			int patternmatchcount=0;
			int matchingindex=-1;
			int currentindex=0;
			for (PrefixMatch cand : match_list) {
			boolean patternmatch=false;
			for (Option candopt : cand.getLevel().getOption()) {
			Matcher matcher = Pattern.compile("^"+candopt.getPattern()+"$").matcher(input);
			  if (matcher.lookingAt()) {
			  	patternmatch=true;
			  }
			}
			if (patternmatch) {
			  	matchingindex=currentindex;
			  	patternmatchcount++;
			}
			currentindex++;
			}
			if ((patternmatchcount == 1) && (matchingindex > -1)) {
			debugprintln("Returning "+match_list.get(matchingindex).getScheme().getName()+" with level "+match_list.get(matchingindex).getLevel().getType()+" and setting tagLength to "+realTagLength);
			return new PrefixMatch2(match_list.get(matchingindex).getScheme(),match_list.get(matchingindex).getLevel(),Integer.toString(realTagLength));
			} else {
			throw new TDTException("More than one scheme/level matched the input value even at pattern level");
			}
		} else {
			debugprintln("Returning "+match_list.get(0).getScheme().getName()+" with level "+match_list.get(0).getLevel().getType()+" and setting tagLength to "+realTagLength);
			return new PrefixMatch2(match_list.get(0).getScheme(),match_list.get(0).getLevel(),Integer.toString(realTagLength));
		}
	}

	/**
	 * Given an input string, level, and optionally a tag length, find a
	 * matching prefix.
	 */
	private PrefixMatch2 findPrefixMatch(String input, String strTagLength,
			LevelTypeList level_type) {
		
		debugprintln("PrefixMatch with 3 parameters: specified strTagLength = "+strTagLength);
		
		int realTagLength=Integer.parseInt(strTagLength);
		List<PrefixMatch> match_list = new ArrayList<PrefixMatch>();
		List<PrefixMatch> alt_match_list = new ArrayList<PrefixMatch>();
		PrefixTree<PrefixMatch> tree = prefix_tree_map.get(level_type);
		assert tree != null;
		List<PrefixMatch> list = tree.search(input);
		if (!list.isEmpty()) {
			if (strTagLength == null)
				match_list.addAll(list);
			else {
				BigInteger tagLength = new BigInteger(strTagLength);
				for (PrefixMatch match : list)
					if (match.getScheme().getTagLength().compareTo(tagLength) == 0) {
						match_list.add(match);
					} else {
						alt_match_list.add(match);
						realTagLength = Integer.parseInt(match.getScheme().getTagLength().toString());
					}
			}
		}
		
		if (match_list.isEmpty()) {
			if (alt_match_list.size() == 1) {
				match_list.add(alt_match_list.get(0));
			}
		}
		
		
		if (match_list.isEmpty()) {
			debugprintln("***EXCPETION: No schemes or levels matched the input value (line 679)");
			throw new TDTException("No schemes or levels matched the input value");
		} else if (match_list.size() > 1) {
			debugprintln("***EXCEPTION: More than one scheme/level matched the input value (line 682)");
			int patternmatchcount=0;
			int matchingindex=-1;
			int currentindex=0;
			for (PrefixMatch cand : match_list) {
			boolean patternmatch=false;
			for (Option candopt : cand.getLevel().getOption()) {
			Matcher matcher = Pattern.compile("^"+candopt.getPattern()+"$").matcher(input);
			  if (matcher.lookingAt()) {
			  	patternmatch=true;
			  }
			}
			if (patternmatch) {
			  	matchingindex=currentindex;
			  	patternmatchcount++;
			}
			currentindex++;
			}
			if ((patternmatchcount == 1) && (matchingindex > -1)) {
			debugprintln("Returning "+match_list.get(matchingindex).getScheme().getName()+" with level "+match_list.get(matchingindex).getLevel().getType()+" and setting tagLength to "+realTagLength);
			return new PrefixMatch2(match_list.get(matchingindex).getScheme(),match_list.get(matchingindex).getLevel(),Integer.toString(realTagLength));
			} else {
			throw new TDTException("More than one scheme/level matched the input value even at pattern level");
			}
		} else {
			return new PrefixMatch2(match_list.get(0).getScheme(),match_list.get(0).getLevel(),Integer.toString(realTagLength));
		}

	}



	/**
	 * Translates the input string of a specified input level to a specified
	 * outbound level of the same coding scheme. For example, the input string
	 * value may be a tag-encoding URI and the outbound level specified by
	 * string outboundlevel may be BINARY, in which case the return value is a
	 * binary representation expressed as a string.
	 * 
	 * <p>
	 * Note that this version of the method requires that the user specify the
	 * input level, rather than searching for it. However it still automatically
	 * finds the scheme used.
	 * </p>
	 * 
	 * @param input
	 *            input tag coding
	 * @param inputLevel
	 *            level such as BINARY, or TAG_ENCODING.
	 * @param tagLength
	 *            tag length such as VALUE_64 or VALUE_96.
	 * @param inputParameters
	 *            a map with any additional properties.
	 * @param outputLevel
	 *            required output level.
	 * @return output tag coding
	 */
	public String convert(String input, LevelTypeList inputLevel,
			String tagLength, Map<String, String> suppliedInputParameters,
			LevelTypeList outputLevel) {

		debugprintln("convert (line 699)");			
		Map<String, String> inputParameters=suppliedInputParameters;
		debugprintln("inputParameters were");
		Iterator i=inputParameters.keySet().iterator();
		while (i.hasNext()) {
			String key = i.next().toString();
			debugprintln(key + "=" + inputParameters.get(key));
		}
		debugprintln("End of inputParameters");

		if (input.startsWith("urn:epc:")) {
		input = uriunescape(input);
		}

		PrefixMatch2 matchtemp = findPrefixMatch(input, tagLength, inputLevel);
		PrefixMatch match = new PrefixMatch(matchtemp.getScheme(),matchtemp.getLevel());
		inputParameters.put("taglength",matchtemp.getTaglength());

		
		// if the input is binary or URI, ignore any value of optionKey that is specified in the input parameters (since its value may contradict the value obtained from pattern matching the input)
		
		
			debugprintln("[line 718] matchtemp.getLevel().getType() = "+matchtemp.getLevel().getType().toString());
		


		return convertLevel(match.getScheme(), match.getLevel(), input, inputParameters, outputLevel);
	}

	/**
	 * The convert method translates a String input to a specified outbound
	 * level of the same coding scheme. For example, the input string value may
	 * be a tag-encoding URI and the outbound level specified by string
	 * outboundlevel may be BINARY, in which case the return value is a binary
	 * representation expressed as a string.
	 * 
	 * @param input
	 *            the identifier to be converted.
	 * @param inputParameters
	 *            additional parameters which need to be provided because they
	 *            cannot always be determined from the input value alone.
	 *            Examples include the taglength, companyprefixlength and filter
	 *            values.
	 * @param outputLevel
	 *            the outbound level required for the ouput. Permitted values
	 *            include BINARY, TAG_ENCODING, PURE_IDENTITY, LEGACY and
	 *            ONS_HOSTNAME.
	 * @return the identifier converted to the output level.
	 */
	public String convert(String input, Map<String, String> suppliedInputParameters,
			LevelTypeList outputLevel) {

		debugprintln("convert (line 748)");
		debugprintln("===============================================");
		debugprintln("CONVERT "+input+" to "+outputLevel.toString());

		Map<String, String> inputParameters=suppliedInputParameters;

		debugprintln("GS1 CP length = "+inputParameters.get("gs1companyprefixlength"));

		
		debugprintln("inputParameters were");
		Iterator i=inputParameters.keySet().iterator();
		while (i.hasNext()) {
			String key = i.next().toString();
			debugprintln(key + "=" + inputParameters.get(key));
		}
		debugprintln("End of inputParameters");

		String tagLength = null;
		String decodedinput;
		String encoded;
		
		if (inputParameters.containsKey("taglength")) {
			// in principle, the user should provide a
			// TagLengthList object in the parameter list.
			String s = inputParameters.get("taglength");
			tagLength = s;
			
			debugprintln("taglength was provided.  tagLength = "+s);
		}

		if (input.startsWith("urn:epc:")) {
		input = uriunescape(input);
		}

		PrefixMatch2 matchtemp = findPrefixMatch(input, tagLength);
		
		PrefixMatch match = new PrefixMatch(matchtemp.getScheme(),matchtemp.getLevel());
		inputParameters.put("taglength",matchtemp.getTaglength());
		debugprintln("Tag length has been set to "+matchtemp.getTaglength());
		
		// if the input is binary or URI, ignore any value of optionKey that is specified in the input parameters (since its value may contradict the value obtained from pattern matching the input)
		
			debugprintln("[line 786] matchtemp.getLevel().getType() = "+matchtemp.getLevel().getType().toString());
		
				debugprintln("reached line 788");
		
		// if a URI is supplied, remember to perform URL decoding on it before passing it to the convertLevel() method
		
		
		// if a URI is returned, remember to perform URL encoding on it before returning it as output
		
		return convertLevel(match.getScheme(), match.getLevel(), input, inputParameters, outputLevel);
				
				
	}

	/**
	 * convert from a particular scheme / level
	 */
	private String convertLevel(Scheme tdtscheme, Level tdtlevel, String input,
			Map<String, String> inputParameters, LevelTypeList outboundlevel) {
		
		
		debugprintln("convertLevel (line 820) - 19:12 21st October 2010");
		debugprintln("===============================================");
		debugprintln("CONVERT "+input+" to "+outboundlevel.toString());
		
		String outboundstring;
		Map<String, String> extraparams =
		// new NoisyMap
		(new HashMap<String, String>(inputParameters));

		// get the scheme's option key, which is the name of a
		// parameter whose value is matched to the option key of the
		// level.

		String optionValue;
		String optionkey = tdtscheme.getOptionKey();
		debugprintln("optionkey for scheme = "+optionkey);
		debugprintln("tdtlevel.getType() = "+tdtlevel.getType().toString());
		if (!((tdtlevel.getType() == LevelTypeList.TAG_ENCODING) || (tdtlevel.getType() == LevelTypeList.PURE_IDENTITY) || (tdtlevel.getType() == LevelTypeList.BINARY) )) {
		optionValue = inputParameters.get(optionkey);
		} else {
		optionValue=null;
		}
		debugprintln("optionValue = "+optionValue);
		
		// the name of a parameter which allows the appropriate option
		// to be selected

		// now consider the various options within the scheme and
		// level for each option element inside the level, check
		// whether the pattern attribute matches as a regular
		// expression

		String matchingOptionKey = null;
		Option matchingOption = null;
		Matcher prefixMatcher = null;
		Map<String,Option> pattern_map = new HashMap<String,Option>();
		Map<String,Matcher> matcher_map = new HashMap<String,Matcher>();

		debugprintln("line 858 input = "+input);
		
		for (Option opt : tdtlevel.getOption()) {
			if (optionValue == null || optionValue.equals(opt.getOptionKey())) {
				// possible match
				debugprintln("optionValue = "+optionValue);
				debugprintln("opt.getOptionKey() = "+opt.getOptionKey());
				debugprintln("Pattern = "+opt.getPattern());
				
				Matcher matcher = Pattern.compile("^"+opt.getPattern()).matcher(input);
				debugprintln("lookingAt ^"+opt.getPattern());
				if (matcher.lookingAt()) {
						debugprintln("MATCHED!");
						pattern_map.put(opt.getOptionKey(),opt);
						matcher_map.put(opt.getOptionKey(),matcher);
				}
			}
		}
		
		debugprintln("Size of pattern_map is "+pattern_map.size());

		if (pattern_map.isEmpty()) {
			debugprintln("***EXCEPTION: No patterns matched (line 879)");
			throw new TDTException("No patterns matched (line 880)");
		}

		if (pattern_map.size() > 1) {
			debugprintln("optionkey = "+optionkey);
			debugprintln("extraparams.get("+optionkey+") = "+extraparams.get(optionkey));
			debugprintln("optionValue = "+optionValue);
		
			if (pattern_map.containsKey(optionValue)) {
				debugprintln("matchingOptionKey = "+optionValue);
				debugprintln("matchingOption has pattern = "+pattern_map.get(optionValue).getPattern());
				matchingOptionKey = optionValue;
				matchingOption=pattern_map.get(optionValue);
				prefixMatcher=matcher_map.get(optionValue);
			}
		} 
		if (pattern_map.size() == 1) {
				debugprintln("matchingOptionKey = "+pattern_map.keySet().iterator().next());
				matchingOptionKey = pattern_map.keySet().iterator().next().toString();
				debugprintln("matchingOption has pattern = "+pattern_map.get(matchingOptionKey).getPattern());
				matchingOption=pattern_map.get(matchingOptionKey);
				prefixMatcher=matcher_map.get(matchingOptionKey);
		}

		optionValue = matchingOptionKey;
		debugprintln("optionValue = "+optionValue);
		
		Level tdtoutlevel = findLevel(tdtscheme, outboundlevel);

		debugprint("tdtoutlevel prefixMatch = ");
		if (tdtoutlevel.getPrefixMatch() != null) {
		debugprintln(tdtoutlevel.getPrefixMatch().toString()); 
		} else {
			debugprintln("null");	
		}
			
		Level tdttagurilevel = findLevel(tdtscheme, LevelTypeList.TAG_ENCODING);
		Level tdtbinarylevel = findLevel(tdtscheme, LevelTypeList.BINARY);

		
		debugprint("tdttagurilevel prefixMatch = ");
		debugprintln(tdttagurilevel.getPrefixMatch().toString());
		
		
		Option tdtoutoption = findOption(tdtoutlevel, optionValue);
		
		debugprint("tdtoutoption pattern = ");
		if (tdtoutoption.getPattern() != null) {
		debugprintln(tdtoutoption.getPattern().toString());
		} else {
			debugprintln("null");	
		}
			
		Option tdttagurioption = findOption(tdttagurilevel, optionValue);
		Option tdtbinaryoption = findOption(tdtbinarylevel, optionValue);

		debugprint("tdttagurioption pattern = ");
		debugprintln(tdttagurioption.getPattern().toString());

		// EXTRACTION of values or each of the fields.
		
		// consider all fields within the matching option
		for (Field field : matchingOption.getField()) {
			BigInteger seq = field.getSeq();
			String strfieldname = field.getName();
			PadDirectionList padDir = field.getPadDir();
			PadDirectionList taguriPadDir;
			PadDirectionList bitPadDir = field.getBitPadDir();
			String padChar = field.getPadChar();
			String taguriPadChar;
			String outPadChar;
			int requiredLength = -1; // -1 indicates that no length is specified
			if (field.getLength() != null) {
				requiredLength = field.getLength().intValue();
			}
			

			debugprintln("---------------------------------------------------------");
			debugprintln("fieldname = "+strfieldname);
			String strfieldvaluematched = prefixMatcher.group(seq.intValue());
			debugprintln("strfieldvaluematched = "+strfieldvaluematched);
			debugprintln("---------------------------------------------------------");

			Field outputfield = findField(tdtoutoption, strfieldname, tdtoutlevel);
//			debugprintln("outputfield characterset = "+outputfield.getCharacterSet().toString());
			
			Field tagurifield = findField(tdttagurioption, strfieldname, tdttagurilevel);
			Field binaryfield = findField(tdtbinaryoption, strfieldname, tdtbinarylevel);


			if (tdtlevel.getType() == LevelTypeList.BINARY ) {
				debugprintln("Converting from BINARY to NON-BINARY - see Figure 9b");
				String result9blayer1;
				String result9blayer2;
				String result9blayer3;
				
				if (binaryfield.getCompaction() != null) {
					if (binaryfield.getBitPadDir() != null) {
						// strip leading/trailing bits at the bitPadDir edge until a multiple of compaction bits is obtained	
						
						
						int intcompaction = -1;
						String strCompaction = binaryfield.getCompaction();
						if (strCompaction.equals("5-bit")) { intcompaction = 5; }
						if (strCompaction.equals("6-bit")) { intcompaction = 6; }
						if (strCompaction.equals("7-bit")) { intcompaction = 7; }
						if (strCompaction.equals("8-bit")) { intcompaction = 8; }
						
						if (intcompaction > -1) {
						result9blayer1 = stripbinarypadding(strfieldvaluematched, binaryfield.getBitPadDir(), intcompaction);
						} else {
						result9blayer1 = strfieldvaluematched;
						debugprintln("Invalid value for compaction");
						}

						} else {
						// do nothing
						result9blayer1 = strfieldvaluematched;
					}
				
				// convert the sequence of bits into characters, considering that each byte may have been compacted, as indicated by the compaction attribute
				
						result9blayer2 = binaryToString(result9blayer1,binaryfield.getCompaction());
				
				// check that the string value only contains characters from the permitted character set
						debugprintln("9b: Checking that result "+result9blayer2+" is within character set "+tagurifield.getCharacterSet());
						checkWithinCharacterSet(strfieldname, result9blayer2, tagurifield.getCharacterSet());
					
				} else {
					if (binaryfield.getBitPadDir() != null) {
						// strip leading/trailing bits at the bitPadDir edge until the first non-zero bit is encountered	

						result9blayer1 = stripbinarypadding(strfieldvaluematched, binaryfield.getBitPadDir(), 0);

					} else {
						// do nothing	
						result9blayer1 = strfieldvaluematched;
					}
				
				// consider the sequence of bits as an unsigned integer and convert this integer into a numeric string
				
						result9blayer2 = bin2dec(result9blayer1);
				
				debugprintln("9b: Intermediate results at layer 2="+result9blayer2);
				
				// check that the numeric value is not less than the specified minimum nor greater than the specified maximum

//*** min/max check should happen at the latest possible stage, as building grammar - not here

					if (result9blayer2.length() > 0) {
						debugprintln("9b: Checking min/max for result9blayer2="+result9blayer2);

						if (tagurifield.getDecimalMinimum() != null) {
							debugprintln("9b: Checking minimum :"+tagurifield.getDecimalMinimum());
							checkMinimum(strfieldname, new BigInteger(result9blayer2), tagurifield.getDecimalMinimum());
						}
						if (tagurifield.getDecimalMaximum() != null) {
							debugprintln("9b: Checking maximum :"+tagurifield.getDecimalMaximum());
							checkMaximum(strfieldname, new BigInteger(result9blayer2), tagurifield.getDecimalMaximum());
						}
					}
					
					debugprintln("9b: end if after checking min/max");
				}
				
				
				debugprintln("9b: Finished checking min/max");
				if (binaryfield.getPadChar() != null) {
					if (tagurifield.getPadChar() != null) {
						// invalid TDT file	
						result9blayer3=result9blayer2;
						debugprintln("9b: Invalid TDT file");
					} else {
						debugprintln("9b: Preparing to strip pad characters if required");
						// strip at the padDir edge any successive instances of the character indicated by padChar attribute (padChar and padDir read from the binary level)	
						result9blayer3 = stripPadChar(result9blayer2, binaryfield.getPadDir(), binaryfield.getPadChar());
					}
				
				} else {
					if (tagurifield.getPadChar() != null) {
						debugprintln("9b: Preparing to apply pad characters if required");
						// pad at the padDir edge with character indicated by padChar attribute to reach a total length of characters indicated by length attribute (padChar, padDir, length read from the binary level)
						result9blayer3 = applyPadChar(result9blayer2, tagurifield.getPadDir(), tagurifield.getPadChar(), tagurifield.getLength().intValue());
					} else {
						// do nothing
						result9blayer3 = result9blayer2;
					}
				
				}
				
				
				
				debugprintln("9b\tFinal result result9blayer3 = "+result9blayer3);
				debugprintln("tagurifield.getLength() = "+tagurifield.getLength());
				if ((tagurifield!=null) && (tagurifield.getLength() != null) && (tagurifield.getLength().intValue() == 0)) {
				extraparams.put(strfieldname,"");
				} else {
				extraparams.put(strfieldname,result9blayer3);
				}
			} else {
	
			
				// this deals with the situation where the input is not BINARY
				debugprintln("Converting from non-binary levels at line 1060");
				debugprintln("Fieldname = "+strfieldname);
				debugprintln("Value = "+strfieldvaluematched);
				debugprintln("Line 1063");
				if (tagurifield != null) {
				debugprintln("Permitted character set = "+tagurifield.getCharacterSet());
				debugprintln("Line 1066");
				debugprintln("Decimal min = "+tagurifield.getDecimalMinimum());
				debugprintln("Decimal max = "+tagurifield.getDecimalMaximum());
				debugprintln("");
				
				// check that the value is within the permitted character set (if this is defined for the field at the TAG_ENCODING level)

// *** min/max check should happen while building grammar - not here.

				if (tagurifield.getCharacterSet() != null) {
					debugprintln("9b else: check character set");
					checkWithinCharacterSet(strfieldname, strfieldvaluematched, tagurifield.getCharacterSet());
				}
				
				// check that the value is not less than the minimum permitted decimal value (if this is defined for the field at the TAG_ENCODING level)				
				if ((tagurifield.getDecimalMinimum() != null) && (strfieldvaluematched.length() > 0)) {
					debugprintln("9b else: checkMin");
					checkMinimum(strfieldname, new BigInteger(strfieldvaluematched), tagurifield.getDecimalMinimum());
				}
				
				// check that the value is not greater than the maximum permitted decimal value (if this is defined for the field at the TAG_ENCODING level)								
				if ((tagurifield.getDecimalMaximum() != null) && (strfieldvaluematched.length() > 0)) {
					debugprintln("9b else: checkMax");
					checkMaximum(strfieldname, new BigInteger(strfieldvaluematched), tagurifield.getDecimalMaximum());
				}

				} else {
				debugprintln("tagurifield was null (field "+strfieldname+" ) is not defined in the tag-encoding URI");
				}

				if (tagurifield != null) {
				debugprintln("tagurifield.getLength() = "+tagurifield.getLength());
				}
				if ((tagurifield!=null) && (tagurifield.getLength() != null) && (tagurifield.getLength().intValue() == 0)) {
				extraparams.put(strfieldname,"");
				} else {
				extraparams.put(strfieldname,strfieldvaluematched);
				}

				
			}
	

			
		} // for each field;

		/**
		 * the EXTRACT rules are performed after parsing the input, in order to
		 * determine additional fields that are to be derived from the fields
		 * obtained by the pattern match process
		 */

		debugprintln("Processing RULE elements of type 'EXTRACT'");
		int seq = 0;
		for (Rule tdtrule : tdtlevel.getRule()) {
			if (tdtrule.getType() == ModeList.EXTRACT) {
				debugprintln("Rule #"+tdtrule.getSeq().intValue()+": "+tdtrule.getNewFieldName());
				assert seq < tdtrule.getSeq().intValue() : "Rule out of sequence order";
				seq = tdtrule.getSeq().intValue();
				processRules(extraparams, tdtrule);
			}
		}

		debugprintln("Finished processing 'EXTRACT' rules");

		/**
		 * Now we need to consider the corresponding output level and output
		 * option. The scheme must remain the same, as must the value of
		 * optionkey (to select the corresponding option element nested within
		 * the required outbound level)
		 */

//		Level tdtoutlevel = findLevel(tdtscheme, outboundlevel);
//		Option tdtoutoption = findOption(tdtoutlevel, optionValue);

		/**
		 * the FORMAT rules are performed before formatting the output, in order
		 * to determine additional fields that are required for preparation of
		 * the outbound format
		 */

		debugprintln("Processing RULE elements of type 'FORMAT'");
		seq = 0;
		for (Rule tdtrule : tdtoutlevel.getRule()) {
			if (tdtrule.getType() == ModeList.FORMAT) {
				debugprintln("Rule #"+tdtrule.getSeq().intValue()+": "+tdtrule.getNewFieldName());
				assert seq < tdtrule.getSeq().intValue() : "Rule out of sequence order";
				seq = tdtrule.getSeq().intValue();
				processRules(extraparams, tdtrule);
			}
		}

		/**
		 * Now we need to ensure that all fields required for the outbound
		 * grammar are suitably padded etc. processPadding takes care of firstly
		 * padding the non-binary fields if padChar and padDir, length are
		 * specified then (if necessary) converting to binary and padding the
		 * binary representation to the left with zeros if the bit string is has
		 * fewer bits than the bitLength attribute specifies. N.B. TDTv1.1 will
		 * be more specific about bit-level padding rather than assuming that it
		 * is always to the left with the zero bit.
		 */

		debugprintln("Finished processing 'FORMAT' rules");

		if (tdtoutlevel.getType() == LevelTypeList.BINARY ) {
		debugprintln("Converting output fields from NON-BINARY to BINARY - see Figure 9a");
		
		for (Field field : tdtoutoption.getField()) {
		
		
			String strfieldname = field.getName();
			Field tagurifield = findField(tdttagurioption, strfieldname, tdttagurilevel);
			Field binaryfield = findField(tdtbinaryoption, strfieldname, tdtbinarylevel);
			String strfieldvaluematched = extraparams.get(strfieldname);

			debugprintln("Output field: "+strfieldname+" had value "+strfieldvaluematched);

			String result9alayer1;
			
			if (tagurifield !=null) {
			
				if (tagurifield.getPadChar() != null) {
					if (binaryfield.getPadChar() != null) {
						debugprintln("9a Invalid TDT definition file");
						result9alayer1="";
					} else {
						// Strip non-binary field of any successive pad characters tagurifield.getPadChar() at edge tagurifield.getPadDir()
						result9alayer1 = stripPadChar(strfieldvaluematched,tagurifield.getPadDir(),tagurifield.getPadChar());
					}
				
				
				} else {
					if (binaryfield.getPadChar() != null) {
						// Pad the non-binary field with pad characters binaryfield.getPadChar() at the edge binaryfield.getPadDir() to reach a total length of binaryfield.getLength() characters
						result9alayer1 = applyPadChar(strfieldvaluematched, binaryfield.getPadDir(), binaryfield.getPadChar(), binaryfield.getLength().intValue());
					} else {
						// do not pad this field at the non-binary level
						result9alayer1 = strfieldvaluematched;
						
					}
				
				}
						debugprintln("\tIntermediate Result for Fig 9a at layer 1="+result9alayer1);
				
				String result9alayer2;
				
				if (binaryfield.getCompaction() != null) {
					// treat the field as an alphanumeric field
					// check that all of its characters are within the allowed character set
					checkWithinCharacterSet(strfieldname, result9alayer1, tagurifield.getCharacterSet());
					// convert to binary using the compaction method specified for that field at binary level
					result9alayer2 = stringToBinary(result9alayer1,binaryfield.getCompaction().toString());
				} else {
					// check that the non-binary value is not less than the minimum nor greater than the maximum value permitted
					if (result9alayer1.length() > 0) {
					checkMinimum(strfieldname, new BigInteger(result9alayer1), tagurifield.getDecimalMinimum());
					checkMaximum(strfieldname, new BigInteger(result9alayer1), tagurifield.getDecimalMaximum());
					}
					// treat the numeric field as as an unsigned integer and convert this integer into a sequence of bits
					result9alayer2 = dec2bin(result9alayer1);
				}
				
					debugprintln("\tIntermediate Result for Fig 9a at layer 2="+result9alayer2);
				
				
				String result9alayer3;
				
				if (binaryfield.getBitPadDir() != null) {
					debugprintln("9a Pad with leading/trailing bits at the "+binaryfield.getBitPadDir()+" edge to reach a total of "+binaryfield.getBitLength()+" bits");
					result9alayer3 = applyPadChar(result9alayer2, binaryfield.getBitPadDir(), "0", binaryfield.getBitLength().intValue());
				} else {
					debugprintln("9a Don't pad at binary level");
					result9alayer3 = result9alayer2;
				}
				
						debugprintln("\tFinal Result for Fig 9a at layer 3="+result9alayer3);

						debugprintln("Need to put this value into extraparams as the value for key "+strfieldname);
						
						debugprintln("binaryfield.getBitLength() = "+binaryfield.getBitLength());
						
						if ((binaryfield.getBitLength() != null) && (binaryfield.getBitLength().intValue() == 0)) {
						extraparams.put(strfieldname,"");
						} else {
						extraparams.put(strfieldname,result9alayer3);
						}
			} else {
				String result9alayer3;
				
				if (binaryfield.getBitPadDir() != null) {
					debugprintln("9a Pad with leading/trailing bits at the "+binaryfield.getBitPadDir()+" edge to reach a total of "+binaryfield.getBitLength()+" bits");
					result9alayer3 = applyPadChar(dec2bin(strfieldvaluematched), binaryfield.getBitPadDir(), "0", binaryfield.getBitLength().intValue());
				} else {
					debugprintln("9a Don't pad at binary level");
					result9alayer3 = dec2bin(strfieldvaluematched);
				}
				
				debugprintln("binaryfield.getBitLength() = "+binaryfield.getBitLength());

				if ((binaryfield.getBitLength() != null) && (binaryfield.getBitLength().intValue() == 0)) {
				extraparams.put(strfieldname,"");
				} else {
				extraparams.put(strfieldname,result9alayer3);
				}


			}
		
		}
		
		}






/*		// debugprintln(" prior to processPadding, " + extraparams);
		for (Field field : tdtoutoption.getField()) {
			// processPadding(extraparams, field, outboundlevel, tdtoutoption);
			
			
			// *** TDT 1.4 TODO this might need some updating
			
			padField(extraparams, field);
			if (outboundlevel == LevelTypeList.BINARY)
				binaryPadding(extraparams, field);
		}
*/
		/**
		 * Construct the output from the specified grammar (in ABNF format)
		 * together with the field values stored in inputparams
		 */
		debugprintln("Building final grammar");


// *** need to do check min/max just before building grammar - not earlier
// *** may need to pass additional fields into buildGrammar in order to do this

// *** logic is flawed here.  We cannot test for fields that do not appear in the grammar string
// *** instead we need to extract these from the grammar string and check against constraints expressed in either the rules of type="FORMAT" or the field in tdtoutoption.
		
		for (Field testfield : tdtoutoption.getField()) {
		String testfieldname = testfield.getName();
		debugprintln("Field to be checked: "+testfieldname+" = "+extraparams.get(testfieldname));
		if (outboundlevel == LevelTypeList.BINARY) {
			Field tagurifield = findField(tdttagurioption, testfieldname, tdttagurilevel);
			if (tagurifield.getDecimalMinimum() != null) {
			debugprintln("Decimal minimum = "+tagurifield.getDecimalMinimum());
			checkMinimum(testfieldname, new BigInteger(bin2dec(extraparams.get(testfieldname))), testfield.getDecimalMinimum());			
			}
			if (tagurifield.getDecimalMaximum() != null) {
			debugprintln("Decimal maximum = "+tagurifield.getDecimalMaximum());
			checkMaximum(testfieldname, new BigInteger(bin2dec(extraparams.get(testfieldname))), testfield.getDecimalMaximum());			
			}
		} else {
			if (testfield.getDecimalMinimum() != null) {
			debugprintln("Decimal minimum = "+testfield.getDecimalMinimum());
			checkMinimum(testfieldname, new BigInteger(extraparams.get(testfieldname)), testfield.getDecimalMinimum());			
			}
			if (testfield.getDecimalMaximum() != null) {
			debugprintln("Decimal maximum = "+testfield.getDecimalMaximum());
			checkMaximum(testfieldname, new BigInteger(extraparams.get(testfieldname)), testfield.getDecimalMaximum());
			}
			if (testfield.getCharacterSet() != null) {
			debugprintln("Character set = "+testfield.getCharacterSet());
			checkWithinCharacterSet(testfieldname, extraparams.get(testfieldname), testfield.getCharacterSet());
			}
		}
		}

		// need to get fields for tdtoutoption
		// then check each one for min/max, charSet

		outboundstring = buildGrammar(tdtoutoption.getGrammar(), extraparams, outboundlevel);

		// debugprintln("final extraparams = " + extraparams);
		debugprintln("RESULT after building grammar = " + outboundstring);
		debugprintln("===============================================================================");
		debugprintln("");
		return outboundstring;
	}

	/**
	 * 
	 * Converts a binary string into a large integer (numeric string)
	 */
	public String bin2dec(String binary) {
		
		debugprintln("(line 1285) binary = "+binary);
		if (binary.length() == 0) {
		return "0";
		} else {
		debugprintln("Converting binary to decimal");
		BigInteger dec = new BigInteger(binary, 2);
		debugprintln("Decimal value = "+dec.toString());
		return dec.toString();
		}
	}

	/**
	 * 
	 * Converts a large integer (numeric string) to a binary string
	 */
	public String dec2bin(String decimal) {
		// TODO: required?
		if (decimal == null) {
			decimal = "1";
		}
		
		debugprintln("(line 1301) decimal = "+decimal);
		if (decimal.length() == 0) {
		return "0";
		} else {
		BigInteger bin = new BigInteger(decimal);
		return bin.toString(2);
		}
	}


	private StringBuffer replaceStringBuffer(StringBuffer buffer, String value) {
		buffer.replace(0,buffer.length(),value);
		return buffer;
	}
	
	
	private void checkWithinCharacterSet(String fieldname, String value, String characterset) {
		if (characterset != null) { 
			// if the character set is specified
			// check that the entire strfieldvalue consists only of characters
			// permitted within the permitted character set for that field
			// according to the characterSet attribute

			String appendedCharacterSet;
			if (characterset.endsWith("*")) {
				appendedCharacterSet=characterset;
				} else {
				appendedCharacterSet=characterset+"*";
				}
			Matcher charsetmatcher = Pattern.compile("^" + appendedCharacterSet + "$").matcher(value);

			// if any invalid characters are found, throw a new TDT Exception
			if (!charsetmatcher.matches()) {
				debugprintln("***EXCEPTION: field "+ fieldname+ " ("+ value+ ") does not conform to the allowed character set ("+ characterset + ") ");
				//throw new TDTException("field "+ fieldname+ " ("+ value+ ") does not conform to the allowed character set ("+ characterset + ") ");
			}
		}
	}
	
	private void checkMinimum(String fieldname, BigInteger bigvalue, String decimalminimum) {
	
	// if decimalMinimum is specified, check that the field is not less than the minimum value permitted by decimalMinimum
	if (decimalminimum != null) { 
		
		BigInteger bigmin = new BigInteger(decimalminimum);
		
		if (bigvalue.compareTo(bigmin) == -1) { 
			// throw an exception if the field value is less than the decimal minimum
			debugprintln("***EXCEPTION: field " + fieldname + " (" + bigvalue + ") is less than DecimalMinimum (" + decimalminimum + ") allowed");
			//throw new TDTException("field " + fieldname + " (" + bigvalue + ") is less than DecimalMinimum (" + decimalminimum + ") allowed");
		}
	}
	}
		
	private void checkMaximum(String fieldname, BigInteger bigvalue, String decimalmaximum) {
		// if decimalMaximum is specified, check that the field is not greater than the maximum value permitted by decimalMaximum
	if (decimalmaximum != null) {
		
		BigInteger bigmax = new BigInteger(decimalmaximum);
		
		if (bigvalue.compareTo(bigmax) == 1) {
 			// throw an exception if the field value is greater than the decimal maximum
			debugprintln("***EXCEPTION: field " + fieldname + " (" + bigvalue + ") is greater than DecimalMaximum (" + decimalmaximum + ") allowed");
			// throw new TDTException("field " + fieldname + " (" + bigvalue + ") is greater than DecimalMaximum (" + decimalmaximum + ") allowed");
		}
	}
	}
	
	
	
	/**
	 * 
	 * Converts a binary string to a character string according to the specified compaction
	 *
	 */
	private String binaryToString(String value, String compaction) {
	String s;
	if ("5-bit".equals(compaction)) {
		// "5-bit"
	s = bin2uppercasefive(value);
	} else if ("6-bit".equals(compaction)) {
	// 6-bit
	s = bin2alphanumsix(value);
	} else if ("7-bit".equals(compaction)) {
	// 7-bit
	s = bin2asciiseven(value);
	} else if ("8-bit".equals(compaction)) {
	// 8-bit
	s = bin2bytestring(value);
	} else {
		debugprintln("***ERROR: unsupported compaction method " + compaction);
		throw new Error("unsupported compaction method " + compaction);
	}
	return s;
	}
						

/**
 * 
 * Converts a hexadecimal string to a binary string
 * Note that this method ensures that the binary string has leading zeros
 * in order to reach a length corresponding to 4 times the length of the hex string
 * Note that the actual binary string to be provided to the convert() method may need between 1 and 3 leading zeros to be truncated
 * An example is SGLN-195, where the hex representation would be padded to 49 hex characters, resulting in 196 bits after hex2bin
 * so we would need to try firstly converting 196 bits (i.e. offset 0), then 195 bits (offset = 1), then 194 bits (offset=2), then 193 bits (offset=3)
 * until we find one of these which successfully converts.
 */
public String hex2bin(String hex) {
int lenhex = hex.length();

debugprintln("(line 1407) hex = "+hex);
if (hex.length() == 0) {
return "";
} else {
BigInteger bin = new BigInteger(hex.toLowerCase(), 16);
StringBuffer stringbin = new StringBuffer(bin.toString(2)); 
int padlength = lenhex*4 - stringbin.length();
if (padlength > 0) {
stringbin.insert(0,"00000000000000000000000000000000000000000000000000000000000000".substring(0,padlength));
}
return stringbin.toString();
}
}

/**
 * 
 * Converts a binary string to a hexadecimal string
 * Note that this method ensures that the hex string has leading zeros
 * in order to reach a length corresponding to 1/4 of the length of the binary string, rounded up to the nearest integer.
 */
public String bin2hex(String binary) {
int lenbin = binary.length();
int lenhex = ((lenbin + 3)/4);

debugprintln("(line 1428) binary = "+binary);
if (binary.length() == 0) {
return "";
} else {
BigInteger hex = new BigInteger(binary, 2);
StringBuffer rawhex= new StringBuffer(hex.toString(16).toUpperCase());
int padlength = lenhex-rawhex.length();
if (padlength > 0) {
rawhex.insert(0,"0000".substring(0,padlength));
}
return rawhex.toString();
}
}




	/**
	 * Returns a string built using a particular grammar. Single-quotes strings
	 * are counted as literal strings, whereas all other strings appearing in
	 * the grammar require substitution with the corresponding value from the
	 * extraparams hashmap.
	 */
	private String buildGrammar(String grammar, Map<String, String> extraparams, LevelTypeList outboundlevel) {
		StringBuilder outboundstring = new StringBuilder();
		String[] fields = Pattern.compile("\\s+").split(grammar);
		for (int i = 0; i < fields.length; i++) {
			String formattedparam;
			if (fields[i].substring(0, 1).equals("'")) {
				formattedparam=fields[i].substring(1,fields[i].length() - 1);
			} else {
				if ((outboundlevel == LevelTypeList.TAG_ENCODING) || (outboundlevel == LevelTypeList.PURE_IDENTITY)) {
					
					formattedparam = uriescape(extraparams.get(fields[i]));
		debugprintln("(line 1484) param = "+extraparams.get(fields[i]));
		debugprintln("(line 1485) formattedparam = "+formattedparam);
					
				} else {
				formattedparam = extraparams.get(fields[i]);
				}
			}
			
			outboundstring.append(formattedparam);
			debugprintln("buildGrammar appending outboundstring with "+formattedparam);
		}

		debugprintln("buildGrammar outboundstring = "+outboundstring.toString());
		return outboundstring.toString();
	}

	/**
	 * 
	 * Converts the value of a specified fieldname from the extraparams map into
	 * binary, either handling it as a large integer or taking into account the
	 * compaction of each ASCII byte that is specified in the TDT definition
	 * file for that particular field
	 */
	private String fieldToBinary(Field field, Map<String, String> extraparams) {
		// really need an index to find field number given fieldname;

		String fieldname = field.getName();
		String value = extraparams.get(fieldname);
		String compaction = field.getCompaction();

		if (compaction == null) {
			value = dec2bin(value);
		} else {
			value = stringToBinary(value, compaction);
		}

		return value;
	}

	
	/**
	 * 
	 * Converts a character string to a binary string according to the specified compaction
	 *
	 */
	private String stringToBinary(String value, String compaction) {
		String s;
		if ("5-bit".equals(compaction)) {
			// "5-bit"
			s = uppercasefive2bin(value);
		} else if ("6-bit".equals(compaction)) {
			// 6-bit
			s = alphanumsix2bin(value);
		} else if ("7-bit".equals(compaction)) {
			// 7-bit
			s = asciiseven2bin(value);
		} else if ("8-bit".equals(compaction)) {
			// 8-bit
			s = bytestring2bin(value);
		} else {
			debugprintln("***ERROR: unsupported compaction method " + compaction);		
			throw new Error("unsupported compaction method " + compaction);
		}
		return s;
	}
	
	
	/**
	 * pad a value according the field definition.
	 */
	private void padField(Map<String, String> extraparams, Field field) {
		String name = field.getName();
		String value = extraparams.get(name);
		PadDirectionList padDir = field.getPadDir();
		PadDirectionList bitPadDir = field.getBitPadDir();

		debugprintln("Line 1560 (padField), outputfield ["+name+"] = "+value);
		if (bitPadDir != null) {
			if (bitPadDir == PadDirectionList.RIGHT) {
				debugprintln("Line 1563 (padField), bitPadDir = RIGHT");
			} else {
				debugprintln("Line 1565 (padField), bitPadDir = LEFT");
			}
		}
			
		if (padDir != null) {
			if (padDir == PadDirectionList.RIGHT) {
				debugprintln("Line 1571 (padField), padDir = RIGHT");
			} else {
				debugprintln("Line 1573 (padField), padDir = LEFT");
			}
		}
				
				
		int requiredLength = -1; // -1 indicates that the length is unspecified
		if (field.getLength() != null) {
			requiredLength = field.getLength().intValue();
		}

		debugprintln("Line 1583 (padField), requiredLength ["+name+"] = "+requiredLength);
			
		
		// assert value != null;
		if (value == null)
			return;

		String padCharString = field.getPadChar();
		// if no pad char specified, don't attempt padding
		if (padCharString == null)
			return;
		assert padCharString.length() > 0;
		char padChar = padCharString.charAt(0);

		String paddedvalue;
		if ((value != null) && (value.toString().length() < requiredLength) && (padCharString !=null) && (requiredLength >=0)) {
			paddedvalue = applyPadChar(value, padDir, padCharString, requiredLength);
			debugprintln("Line 1600 (padField), paddedvalue = "+paddedvalue);
		} else {
			paddedvalue = value;	
			debugprintln("Line 1603 (padField), No need for padding");
		}

		
		if (requiredLength != value.length()) {
			extraparams.put(name,paddedvalue);
		}
		
		if (requiredLength == 0) {
			extraparams.put(name,"");
		}

	}

	/**
	 * If the outbound level is BINARY, convert the string field to binary, then
	 * pad to the left with the appropriate number of zero bits to reach a
	 * number of bits specified by the bitLength attribute of the TDT definition
	 * file.
	 */

	private void binaryPadding(Map<String, String> extraparams, Field tdtfield) {
		String fieldname = tdtfield.getName();
		int reqbitlength = tdtfield.getBitLength().intValue();
		PadDirectionList bitPadDir = tdtfield.getBitPadDir();

		String binarypaddedvalue;

		String binaryValue = fieldToBinary(tdtfield, extraparams);
		debugprintln("binarypadding: binaryValue = "+binaryValue);
		
		if (binaryValue.length() < reqbitlength) {
			
			if (bitPadDir != null) {
			    binarypaddedvalue = applyPadChar(binaryValue, bitPadDir, "0", reqbitlength);
			} else {
				// Default to binary padding at the left if bitPadDir is unspecified.
				// This is for backwards compatibility with TDT 1.0 definition files that lack bitPadDir
				binarypaddedvalue = applyPadChar(binaryValue, PadDirectionList.LEFT, "0", reqbitlength); 
			}
			
		} else {
			if (binaryValue.length() > reqbitlength) {
				debugprintln("***EXCEPTION: Binary value [" + binaryValue + "] for field " + fieldname + " exceeds maximum allowed " + reqbitlength + " bits.  Decimal value was " + extraparams.get(fieldname)); 
				throw new TDTException("Binary value [" + binaryValue + "] for field " + fieldname + " exceeds maximum allowed " + reqbitlength + " bits.  Decimal value was " + extraparams.get(fieldname)); 
			}
			
			binarypaddedvalue = binaryValue;
		}
		
		if (reqbitlength==0) {
		binarypaddedvalue="";
		}
		
		debugprintln("binarypadding: binarypaddedalue = "+binarypaddedvalue);

		
		extraparams.put(fieldname, binarypaddedvalue);
	}

	/**
	 * Removes leading or trailing characters equal to padchar from the
	 * start/end of the string specified as the first parameter. The second
	 * parameter specified the stripping direction as "LEFT" or "RIGHT" and the
	 * third parameter specifies the character to be stripped.
	 */
	private String stripPadChar(String padded, PadDirectionList dir, String padchar) {
		String rv;
		String onlypadcharpattern="^["+padchar+"]+$";
		
		Pattern testregex = Pattern.compile(onlypadcharpattern);
						
		// check if line includes filename.xml - if so, extract auxiliaryfile           
		Matcher testmatcher2 = testregex.matcher(padded);
		if (testmatcher2.find()) {
		rv=padchar;
		} else {
		if (dir == null || padchar == null)
			rv = padded;
		else {
			String pattern;
			if (dir == PadDirectionList.RIGHT)
				pattern = "[" + padchar + "]+$";
			else
				// if (dir == PadDirectionList.LEFT)
				pattern = "^[" + padchar + "]+";

			rv = padded.replaceAll(pattern, "");

		}
		}
		return rv;
	}
	
	
	/**
	 * Applies leading or trailing characters equal to padchar from the
	 * start/end of the string specified as the first parameter. 
	 * The second parameter specified the stripping direction as "LEFT" or "RIGHT". 
	 * The third parameter specifies the character to be used for padding.
	 * The fourth parameter specifies the required length for the string.
	 */
	private String applyPadChar(String bare, PadDirectionList dir, String padchar, int requiredLength) {
		String rv;
		if (dir == null || padchar == null || requiredLength == -1)
			rv = bare;
		else {
			StringBuilder buf = new StringBuilder(requiredLength);
			for (int i=0; i < requiredLength - bare.length(); i++)
				buf.append(padchar);

			if (dir == PadDirectionList.RIGHT)
				rv = bare+buf.toString();
			else
				// if (dir == PadDirectionList.LEFT)
				rv = buf.toString()+bare;
		}
		return rv;
	}
	

	
	private String stripbinarypadding(String input, PadDirectionList bitPadDir, int compaction) {
		
		String stripped;

		Pattern testregex = Pattern.compile("^0+$");
						
		// check if line includes filename.xml - if so, extract auxiliaryfile           
		Matcher testmatcher2 = testregex.matcher(input);
		if (testmatcher2.find()) {
		stripped="0";
		} else {

		
		if (compaction >=4) {
		
			if (bitPadDir == PadDirectionList.RIGHT) {
				int lastnonzerobit = input.lastIndexOf("1");
				int bitsforstripped = compaction * (1 + lastnonzerobit/compaction);
				stripped = input.substring(0,bitsforstripped);
			} else {
				int firstnonzerobit = input.indexOf("1");
				int length = input.length();
				int bitsforstripped = compaction * (1+ (length - firstnonzerobit)/compaction);
				stripped = input.substring(length-bitsforstripped);
			}
		
		} else {
			if (bitPadDir == PadDirectionList.RIGHT) {
				int lastnonzerobit = input.lastIndexOf("1");
				stripped = input.substring(0,lastnonzerobit);
			} else {
				int firstnonzerobit = input.indexOf("1");
				stripped = input.substring(firstnonzerobit);
			}
		
		}
		}
		
		return stripped;	
		
	}
	
	
	

	/**
	 * 
	 * Adds additional entries to the extraparams hashmap by processing various
	 * rules defined in the TDT definition files. Typically used for string
	 * processing functions, lookup in tables, calculation of check digits etc.
	 */
	private void processRules(Map<String, String> extraparams, Rule tdtrule) {
		String tdtfunction = tdtrule.getFunction();
		int openbracket = tdtfunction.indexOf("(");
		assert openbracket != -1;
		String params = tdtfunction.substring(openbracket + 1, tdtfunction
				.length() - 1);
		String rulename = tdtfunction.substring(0, openbracket);
		String[] parameter = params.split(",");
		String newfieldname = tdtrule.getNewFieldName();
		
		debugprintln("Rule: newfieldname = "+newfieldname);
		debugprintln(tdtfunction + " " + parameter[0] + " " + extraparams.get(parameter[0]));
		/**
		 * Stores in the hashmap extraparams the value obtained from a lookup in
		 * a specified XML table.
		 * 
		 * The first parameter is the given value already known. This is denoted
		 * as $1 in the corresponding XPath expression
		 * 
		 * The second parameter is the string filename of the table which must
		 * be present in the auxiliary subdirectory
		 * 
		 * The third parameter is the column in which the supplied input value
		 * should be sought
		 * 
		 * The fourth parameter is the column whose value should be read for the
		 * corresponding row, in order to obtain the result of the lookup.
		 * 
		 * The rule in the definition file may contain an XPath expression and a
		 * URL where the table may be obtained.
		 */
		if (rulename.equals("TABLELOOKUP")) {
			// parameter[0] is given value
			// parameter[1] is table
			// parameter[2] is input column supplied
			// parameter[3] is output column required
			assert parameter.length == 4 : "incorrect number of parameters to tablelookup "
					+ params;
			if (parameter[1].equals("tdt64bitcpi")) {
				String s = extraparams.get(parameter[0]);
				assert s != null : tdtfunction + " when " + parameter[0]
						+ " is null";
				String t = gs1cpi.get(s);
				assert t != null : "gs1cpi[" + s + "] is null";
				assert newfieldname != null;
				extraparams.put(newfieldname, t);
				debugprintln("Rule result: "+newfieldname+" = "+t);
				// extraparams.put(newfieldname,
				// gs1cpi.get(extraparams.get(parameter[0])));
			} else { // JPB! the following is untested
				String tdtxpath = tdtrule.getTableXPath();
				String tdttableurl = tdtrule.getTableURL();
				String tdtxpathsub = tdtxpath.replaceAll("\\$1", extraparams.get(parameter[0]));
				extraparams.put(newfieldname, xpathlookup("ManagerTranslation.xml", tdtxpathsub));
				debugprintln("TABLELOOKUP Rule result: "+newfieldname+" = "+xpathlookup("ManagerTranslation.xml", tdtxpathsub));
			}
		}

		/**
		 * Stores the length of the specified string under the new fieldname
		 * specified by the corresponding rule of the definition file.
		 */
		if (rulename.equals("LENGTH")) {
			assert extraparams.get(parameter[0]) != null : tdtfunction
					+ " when " + parameter[0] + " is null";
			if (extraparams.get(parameter[0]) != null) {
				extraparams.put(newfieldname, Integer.toString(extraparams.get(parameter[0]).length()));
				debugprintln("LENGTH Rule result: "+newfieldname+" = "+Integer.toString(extraparams.get(parameter[0]).length()));
			}
		}

		/**
		 * Stores a GS1 check digit in the extraparams hashmap, keyed under the
		 * new fieldname specified by the corresponding rule of the definition
		 * file.
		 */
		if (rulename.equals("GS1CHECKSUM")) {
			assert extraparams.get(parameter[0]) != null : tdtfunction
					+ " when " + parameter[0] + " is null";
			if (extraparams.get(parameter[0]) != null) {
				extraparams.put(newfieldname, gs1checksum(extraparams.get(parameter[0])));
				debugprintln("GS1CHECKSUM Rule result: "+newfieldname+" = "+gs1checksum(extraparams.get(parameter[0])));

			}
		}

		/**
		 * Obtains a substring of the string provided as the first parameter. If
		 * only a single second parameter is specified, then this is considered
		 * as the start index and all characters from the start index onwards
		 * are stored in the extraparams hashmap under the key named
		 * 'newfieldname' in the corresponding rule of the definition file. If a
		 * second and third parameter are specified, then the second parameter
		 * is the start index and the third is the length of characters
		 * required. A substring consisting characters from the start index up
		 * to the required length of characters is stored in the extraparams
		 * hashmap, keyed under the new fieldname specified by the corresponding
		 * rule of the defintion file.
		 */
		if (rulename.equals("SUBSTR")) {
			assert extraparams.get(parameter[0]) != null : tdtfunction
					+ " when " + parameter[0] + " is null";
			if (parameter.length == 2) {
				if (extraparams.get(parameter[0]) != null) {
					int start = getIntValue(parameter[1], extraparams);
					if (start >= 0) {
						extraparams.put(newfieldname, extraparams.get(parameter[0]).substring(start));
						debugprintln("SUBSTR Rule result: "+newfieldname+" = "+extraparams.get(parameter[0]).substring(start));

					}
				}

			}
			if (parameter.length == 3) { // need to check that this variation is
				// correct - c.f. Perl substr
				assert extraparams.get(parameter[0]) != null : tdtfunction
						+ " when " + parameter[0] + " is null";
				if (extraparams.get(parameter[0]) != null) {
					int start = getIntValue(parameter[1], extraparams);
					int end = getIntValue(parameter[2], extraparams);
					if ((start >= 0) && (end >= 0)) {
						extraparams.put(newfieldname, extraparams.get(parameter[0]).substring(start, start + end));
						debugprintln("SUBSTR Rule result: "+newfieldname+" = "+extraparams.get(parameter[0]).substring(start, start + end));
					}
				}

			}
		}

		/**
		 * Concatenates specified string parameters together. Literal values
		 * must be enclosed within single or double quotes or consist of
		 * unquoted digits. Other unquoted strings are considered as fieldnames
		 * and the corresponding value from the extraparams hashmap are
		 * inserted. The result of the concatenation (and substitution) of the
		 * strings is stored as a new entry in the extraparams hashmap, keyed
		 * under the new fieldname specified by the rule.
		 */
		if (rulename.equals("CONCAT")) {
			StringBuilder buffer = new StringBuilder();
			for (int p1 = 0; p1 < parameter.length; p1++) {
				Matcher matcher = Pattern.compile("\"(.*?)\"|'(.*?)'|[0-9]")
						.matcher(parameter[p1]);
				if (matcher.matches()) {
					buffer.append(parameter[p1]);
				} else {
					assert extraparams.get(parameter[p1]) != null : tdtfunction
							+ " when " + parameter[p1] + " is null";
					if (extraparams.get(parameter[p1]) != null) {
						buffer.append(extraparams.get(parameter[p1]));
					}
				}

			}
			extraparams.put(newfieldname, buffer.toString());
			debugprintln("CONCAT Rule result: "+newfieldname+" = "+buffer.toString());
		}
		
		
		
		//*** TDT 1.4 need to code for additional rules introduced in TDT 1.4 - mainly arithmetic stuff
		

		/**
		 * Adds specified parameters together. Unqouted strings are considered
		 * as fieldnames and the corresponding value from the extraparams hashmap
		 * are used in the calculation. 
		 * The result of the addition is stored as a new entry in the extraparams
		 * hashmap, keyed under the new fieldname specified by the rule.
		 */
		if (rulename.equalsIgnoreCase("add")) {
			assert extraparams.get(parameter[0]) != null : tdtfunction + " when " + parameter[0] + " is null";

			if ((extraparams.get(parameter[0]) != null) && (parameter[1] != null) && (parameter.length == 2)) {
			
				int initialvalue = getIntValue(parameter[0], extraparams);
				int increment = Integer.parseInt(parameter[1]);
				extraparams.put(newfieldname, Integer.toString(initialvalue+increment));
			}
		}



		/**
		 * Multiplies the specified parameters together. 
		 * Unquoted strings are considered as fieldnames and the corresponding
		 * value from the extraparams hashmap are used in the calculation. 
		 * The result of the multiplication is stored as a new entry in the 
		 * extraparams hashmap, keyed under the new fieldname specified by the rule.
		 */
		if (rulename.equalsIgnoreCase("multiply")) {
			assert extraparams.get(parameter[0]) != null : tdtfunction + " when " + parameter[0] + " is null";

			if ((extraparams.get(parameter[0]) != null) && (parameter[1] != null) && (parameter.length == 2)) {
			
				int initialvalue = getIntValue(parameter[0], extraparams);
				int factor = Integer.parseInt(parameter[1]);
				extraparams.put(newfieldname, Integer.toString(initialvalue*factor));
			}
		}


		/**
		 * Divides the first parameter by the second parameter. 
		 * Unquoted strings are considered as fieldnames and the corresponding 
		 * value from the extraparams hashmap are used in the calculation. 
		 * The result of the division is stored as a new entry in the 
		 * extraparams hashmap, keyed under the new fieldname specified by the rule.
		 */
		if (rulename.equalsIgnoreCase("divide")) {
			assert extraparams.get(parameter[0]) != null : tdtfunction + " when " + parameter[0] + " is null";

			if ((extraparams.get(parameter[0]) != null) && (parameter[1] != null) && (parameter.length == 2)) {
			
				int initialvalue = getIntValue(parameter[0], extraparams);
				int divisor = Integer.parseInt(parameter[1]);
				extraparams.put(newfieldname, Integer.toString(initialvalue*divisor));
			}
		}

		/**
		 * Subtracts the second parameter from the first parameter. 
		 * Unquoted strings are considered as fieldnames and the corresponding 
		 * value from the extraparams hashmap are used in the calculation. 
		 * The result of the subtraction is stored as a new entry 
		 * in the extraparams hashmap, keyed under the new fieldname specified
		 * by the rule.
		 */
		if (rulename.equalsIgnoreCase("subtract")) {
			assert extraparams.get(parameter[0]) != null : tdtfunction + " when " + parameter[0] + " is null";

			if ((extraparams.get(parameter[0]) != null) && (parameter[1] != null) && (parameter.length == 2)) {
			
				int initialvalue = getIntValue(parameter[0], extraparams);
				int decrement = Integer.parseInt(parameter[1]);
				extraparams.put(newfieldname, Integer.toString(initialvalue-decrement));
			}
		}


		/**
		 * Returns the remainder after integer division of the first parameter
		 * divided by the second parameter. 
		 * Unquoted strings are considered as fieldnames and the corresponding 
		 * value from the extraparams hashmap are used in the calculation. 
		 * The remainder after integer division is stored as a new entry 
		 * in the extraparams hashmap, keyed under the new fieldname specified
		 * by the rule.
		 */
		if (rulename.equalsIgnoreCase("mod")) {
			assert extraparams.get(parameter[0]) != null : tdtfunction + " when " + parameter[0] + " is null";

			if ((extraparams.get(parameter[0]) != null) && (parameter[1] != null) && (parameter.length == 2)) {
			
				int initialvalue = getIntValue(parameter[0], extraparams);
				int divisor = Integer.parseInt(parameter[1]);
				extraparams.put(newfieldname, Integer.toString(initialvalue % divisor));
			}
		}

		
		
		
		
	}

	/**
	 * 
	 * Returns the value of a specified fieldname from the specified hashmap and
	 * returns an integer value or throws an exception if the value is not an
	 * integer
	 */
	private int getIntValue(String fieldname, Map<String, String> extraparams) {
		Matcher checkint = Pattern.compile("^\\d+$").matcher(fieldname);
		int rv;
		if (checkint.matches()) {
			rv = Integer.parseInt(fieldname);
		} else {
			if (extraparams.containsKey(fieldname)) {
				rv = Integer.parseInt(extraparams.get(fieldname));
			} else {
				rv = -1;
				debugprintln("***EXCEPTION: No integer value for " + fieldname + " can be found - check extraparams;");
				throw new TDTException("No integer value for " + fieldname + " can be found - check extraparams;");
			}
		}
		return rv;
	}

	/**
	 * 
	 * Performs an XPATH lookup in an xml document. The document has been loaded
	 * into a private member. The XPATH expression is supplied as the second
	 * string parameter The return value is of type string e.g. this is
	 * currently used primarily for looking up the Company Prefix Index for
	 * encoding a GS1 Company Prefix into a 64-bit EPC tag
	 * 
	 */
	private String xpathlookup(String xml, String expression) {

		try {

			// Parse the XML as a W3C document.
			DocumentBuilder builder = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder();
			Document document = builder.parse(GEPC64xml);

			XPath xpath = XPathFactory.newInstance().newXPath();

			String rv = (String) xpath.evaluate(expression, document,
					XPathConstants.STRING);

			return rv;

		} catch (ParserConfigurationException e) {
			System.err.println("ParserConfigurationException caught...");
			e.printStackTrace();
			return null;
		} catch (XPathExpressionException e) {
			System.err.println("XPathExpressionException caught...");
			e.printStackTrace();
			return null;
		} catch (SAXException e) {
			System.err.println("SAXException caught...");
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			System.err.println("IOException caught...");
			e.printStackTrace();
			return null;
		}

	}

	// auxiliary functions

	/**
	 * 
	 * Converts original characters into URI escape sequences where required
	 */
   private static String uriescape(String in) {
	in = in.replaceAll("%","%25");
   	in = in.replaceAll("\\?","%3F");
   	in = in.replaceAll("\"","%22");
   	in = in.replaceAll("&","%26");
   	in = in.replaceAll("/","%2F");
   	in = in.replaceAll("<","%3C");
   	in = in.replaceAll(">","%3E");
   	in = in.replaceAll("#","%23");   
    return in;
   }


	/**
	 * 
	 * Converts URI escaped characters back into original characters
	 */
   private static String uriunescape(String in) {
	in = in.replaceAll("%25","%");
   	in = in.replaceAll("%3[Ff]","?");
   	in = in.replaceAll("%22","\\");
   	in = in.replaceAll("%26","&");
   	in = in.replaceAll("%2[Ff]","/");
   	in = in.replaceAll("%3[Cc]","<");
   	in = in.replaceAll("%3[Ee]",">");
   	in = in.replaceAll("%23","#");   
    return in;
   }





	/**
	 * 
	 * Converts a binary string input into a byte string, using 8-bits per
	 * character byte
	 */
	private String bytestring2bin(String bytestring) {
		String binary;
		StringBuilder buffer = new StringBuilder("");
		int len = bytestring.length();
		byte[] bytes = bytestring.getBytes();
		for (int i = 0; i < len; i++) {
			buffer.append(padBinary(dec2bin(Integer.toString(bytes[i])), 8));
		}
		binary = buffer.toString();
		return binary;
	}

	/**
	 * 
	 * Converts a byte string input into a binary string, using 8-bits per
	 * character byte
	 */
	private String bin2bytestring(String binary) {
		String bytestring;
		StringBuilder buffer = new StringBuilder("");
		int len = binary.length();
		for (int i = 0; i < len; i += 8) {
			int j = Integer.parseInt(bin2dec(padBinary(binary.substring(i,
					i + 8), 8)));
			buffer.append((char) j);
		}
		bytestring = buffer.toString();
		return bytestring;
	}

	/**
	 * 
	 * Converts an ASCII string input into a binary string, using 7-bit
	 * compaction of each ASCII byte
	 */
	private String asciiseven2bin(String asciiseven) {
		String binary;
		StringBuilder buffer = new StringBuilder("");
		int len = asciiseven.length();
		byte[] bytes = asciiseven.getBytes();
		for (int i = 0; i < len; i++) {
			buffer.append(padBinary(dec2bin(Integer.toString(bytes[i] % 128)),
					8).substring(1, 8));
		}
		binary = buffer.toString();
		return binary;
	}

	/**
	 * 
	 * Converts a binary string input into an ASCII string output, assuming that
	 * 7-bit compaction was used
	 */
	private String bin2asciiseven(String binary) {
		String asciiseven;
		StringBuilder buffer = new StringBuilder("");
		int len = binary.length();
		for (int i = 0; i < len; i += 7) {
			int j = Integer.parseInt(bin2dec(padBinary(binary.substring(i,
					i + 7), 8)));
			buffer.append((char) j);
		}
		asciiseven = buffer.toString();
		return asciiseven;
	}

	/**
	 * Converts an alphanumeric string input into a binary string, using 6-bit
	 * compaction of each ASCII byte
	 */
	private String alphanumsix2bin(String alphanumsix) {
		String binary;
		StringBuilder buffer = new StringBuilder("");
		int len = alphanumsix.length();
		byte[] bytes = alphanumsix.getBytes();
		for (int i = 0; i < len; i++) {
			buffer
					.append(padBinary(dec2bin(Integer.toString(bytes[i] % 64)),
							8).substring(2, 8));
		}
		binary = buffer.toString();
		return binary;
	}

	/**
	 * 
	 * Converts a binary string input into a character string output, assuming
	 * that 6-bit compaction was used
	 */
	private String bin2alphanumsix(String binary) {
		String alphanumsix;
		StringBuilder buffer = new StringBuilder("");
		int len = binary.length();
		for (int i = 0; i < len; i += 6) {
			int j = Integer.parseInt(bin2dec(padBinary(binary.substring(i,
					i + 6), 8)));
			if (j < 32) {
				j += 64;
			}
			buffer.append((char) j);
		}
		alphanumsix = buffer.toString();
		return alphanumsix;
	}

	/**
	 * Converts an upper case character string input into a binary string, using
	 * 5-bit compaction of each ASCII byte
	 */
	private String uppercasefive2bin(String uppercasefive) {
		String binary;
		StringBuilder buffer = new StringBuilder("");
		int len = uppercasefive.length();
		byte[] bytes = uppercasefive.getBytes();
		for (int i = 0; i < len; i++) {
			buffer
					.append(padBinary(dec2bin(Integer.toString(bytes[i] % 32)),
							8).substring(3, 8));
		}
		binary = buffer.toString();
		return binary;
	}

	/**
	 * 
	 * Converts a binary string input into a character string output, assuming
	 * that 5-bit compaction was used
	 */
	private String bin2uppercasefive(String binary) {
		String uppercasefive;
		StringBuilder buffer = new StringBuilder("");
		int len = binary.length();
		for (int i = 0; i < len; i += 5) {
			int j = Integer.parseInt(bin2dec(padBinary(binary.substring(i,
					i + 5), 8)));
			buffer.append((char) (j + 64));
		}
		uppercasefive = buffer.toString();
		return uppercasefive;
	}

	/**
	 * Pads a binary value supplied as a string first parameter to the left with
	 * leading zeros in order to reach a required number of bits, as expressed
	 * by the second parameter, reqlen. Returns a string value corresponding to
	 * the binary value left padded to the required number of bits.
	 */
	private String padBinary(String binary, int reqlen) {
		String rv;
		int l = binary.length();
		int pad = (reqlen - (l % reqlen)) % reqlen;
		StringBuilder buffer = new StringBuilder("");
		for (int i = 0; i < pad; i++) {
			buffer.append("0");
		}
		buffer.append(binary);
		rv = buffer.toString();
		return rv;
	}

	/**
	 * Calculates the check digit for a supplied input string (assuming that the
	 * check digit will be the digit immediately following the supplied input
	 * string). GS1 (formerly EAN.UCC) check digit calculation methods are used.
	 */
	private String gs1checksum(String input) {
		int checksum;
		int weight;
		int total = 0;
		int len = input.length();
		int d;
		for (int i = 0; i < len; i++) {
			if (i % 2 == 0) {
				weight = -3;
			} else {
				weight = -1;
			}
			d = Integer.parseInt(input.substring(len - 1 - i, len - i));
			total += weight * d;
		}
		checksum = (10 + total % 10) % 10;
		return Integer.toString(checksum);
	}

	/**
	 * find a level by its type in a scheme. This involves iterating through the
	 * list of levels. The main reason for doing it this way is to avoid being
	 * dependent on the order in which the levels are coded in the xml, which is
	 * not explicitly constrained.
	 */
	private Level findLevel(Scheme scheme, LevelTypeList levelType) {
		Level level = null;
		for (Level lev : scheme.getLevel()) {
			if (lev.getType() == levelType) {
				level = lev;
//				break;
			}
		}
		if (level == null) {
			debugprintln("***ERROR: Couldn't find type for " + levelType + " in level " + scheme);
			throw new Error("Couldn't find type " + levelType + " in scheme "
					+ scheme);
		}
		return level;
	}

	/**
	 * find a option by its type in a scheme. This involves iterating through
	 * the list of options. The main reason for doing it this way is to avoid
	 * being dependent on the order in which the options are coded in the xml,
	 * which is not explicitly constrained.
	 */
	private Option findOption(Level level, String optionKey) {
		Option option = null;

		for (Option opt : level.getOption()) {
			if (opt.getOptionKey().equals(optionKey)) {
				option = opt;
				break;
			}
		}
		if (option == null) {
			debugprintln("***ERROR: Couldn't find option for " + optionKey + " in level " + level);
			throw new Error("Couldn't find option for " + optionKey + " in level " + level);
		}
		return option;
	}
	
	/**
	 * find a field by its name in an option. This involves iterating through
	 * the list of fields. The main reason for doing it this way is to avoid
	 * being dependent on the order in which the fields are coded in the xml,
	 * which is not explicitly constrained.
	 */
	private Field findField(Option option, String fieldname, Level tdtoutlevel) {
		Field field=null;
		
		for (Field fld : option.getField()) {
			if (fld.getName().equals(fieldname)) {
				field = fld;
				break;
			}
		}
		
		return field;
	}
	
	private void debugprint(String message) {
		if (showdebug) {
			System.out.print(message);
		}
	}
	
	private void debugprintln(String message) {
		if (showdebug) {
			System.out.println(message);
		}
	}
	
	
	
	/** 
	 * adds a list of global company prefixes (GCPs) to the current list of GCPs.
	 * The list of GCPs is used to convert a GTIN and serial or an SSCC to an 
	 * EPC number when the user does not provide length of the GCP.
	 * 
	 * The method expects the individual GCPs to be on a new line each. It is up 
	 * to the user to determine wher the GCPs are read from (normal file, network, 
	 * onsepc.com)
	 * 
	 *  @param inputstream 
	 *  			a reference to a source of GCPs 
	 * @throws IOException 
	 */
	
	public void addListOfGCPs(InputStream source) throws IOException {
		
		BufferedReader br = new BufferedReader(new InputStreamReader(
	            source, "US-ASCII"));
	    try {
	      String line;
	      while ((line = br.readLine()) != null) {
	        //debugprintln(line);
	      }
	    } finally {
	      br.close();
	    }	   
	}
	
	/**
	 * converts a GTIN and serial number to the pure identity representation of an EPC. 
	 * The method looks up the length of the global company prefix from a list that can 
	 * loaded into the TDT engine.
	 * 
	 *  @params gtin
	 *  @params serial
	 *  @returns pure identity EPC
	 *  
	 */ 
	
	public String convertGTINandSerialToPureIdentityEPC(String gtin, String serial) {
		
		return " ";
		
	}
	
	/**
	 * converts a GTIN and serial number to the pure identity representation of an EPC. 
	 * The length of the global company prefix is provided as a method parameter.
	 * 
	 *  @params gtin
	 *  @params serial
	 *  @params length of global company prefix
	 *  @returns pure identity EPC
	 *  
	 */ 
	
	public String convertGTINandSerialToPureIdentityEPC(String gtin, String serial, int gcpLength) {
		
		return " ";
		
	}
	
	/**
	 * converts a pure identity EPC to gtin and serial. 
	 * 
	 *  @params epc in pure identity format
	 *  @returns List with gtin and serial
	 *  
	 */ 
	
	public List<String> convertPureIdentityEPCToGTINandSerial(String EPC) {
		
		return new ArrayList<String>();
		
	}
	
	
	/**
	 * converts a SSCC to the pure identity representation of an EPC. The method looks up 
	 * the length of the global company prefix from a list that can loaded into the TDT 
	 * engine via the addGCPs  
	 * 
	 *  @params gtin
	 *  @params serial
	 *  @returns pure identity EPC
	 *  
	 */ 
	
	public String convertSSCCToPureIdentityEPC(String sscc) {
		
		return " ";
		
	}
	
	/**
	 * converts a SSCC to the pure identity representation of an EPC. 
	 * The length of the global company prefix is provided as a method parameter.
	 *  @params gtin
	 *  @params serial
	 *  @params length of global company prefix
	 *  @returns pure identity EPC
	 *  
	 */ 
	
	public String convertSSCCToPureIdentityEPC(String sscc, int gcpLength) {
		
		return " ";
		
	}
	
	/**
	 * converts a pure identity EPC to gtin and serial. 
	 * 
	 *  @params epc in pure identity format
	 *  @returns List with gtin and serial
	 *  
	 */ 
	
	public String convertPureIdentityEPCToSSCC(String EPC) {
		
		return " ";
		
	}
	
	
	
	/**
	 * converts a GLN and serial to the pure identity representation of an EPC. The method looks up 
	 * the length of the global company prefix from a list that can loaded into the TDT 
	 * engine. 
	 * 
	 *  @params gtin
	 *  @params serial
	 *  @returns pure identity EPC
	 *  
	 */ 
	
	public String convertGLNandSerialToPureIdentityEPC(String gln, String serial) {
		
		return " ";
		
	}
	
	/**
	 * converts a GLN and serial to the pure identity representation of an EPC.
	 * The length of the global company prefix is provided as a method parameter.
	 *  @params gtin
	 *  @params serial
	 *  @params length of global company prefix
	 *  @returns pure identity EPC
	 *  
	 */ 
	
	public String convertGLNandSerialToPureIdentityEPC(String gln, String serial, int gcpLength) {
		
		return " ";
		
	}
	
	
	
	/**
	 * converts a binary EPC to a pure identity representation. 
	 *  @params binary EPC
	 *  @returns pure identity EPC
	 *  
	 */ 
	
	public String convertBinaryEPCToPureIdentityEPC(String binary) {
		
		return " ";
		
	}
	
	/**
	 * converts a binary EPC in hex notation to a pure identity representation. 
	 *  @params hexadecimal EPC
	 *  @returns pure identity EPC
	 *  
	 */ 
	
	public String convertHexEPCToPureIdentityEPC(String binary) {
		
		return " ";
		
	}
	
	public String getVersion() {
			return "Fosstrak TDT 1.4.0 for TDT v1.4; 2010-110-13 22:29";
	}
	
}

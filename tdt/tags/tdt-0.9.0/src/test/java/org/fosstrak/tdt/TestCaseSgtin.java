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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.sf.csv4j.CSVFieldMapProcessor;
import net.sf.csv4j.CSVFileProcessor;
import net.sf.csv4j.ParseException;
import net.sf.csv4j.ProcessingException;

import org.epcglobalinc.tdt.LevelTypeList;

public class TestCaseSgtin extends TestCase
{

    private TDTEngine engine = null;

    private Map<String,String> params;

    protected void setUp() {
	params = new HashMap<String,String>();
	if (engine == null) {
	    try {
		String s = System.getenv("TDT_PATH");
		if (s == null) s = "target/classes";
		engine = new TDTEngine();
	    }
	    catch (Exception e) {
		e.printStackTrace(System.err);
		//System.exit(1);
	    }
	}
    }
    
    public void testPage13Staged() {
	// this test follows fig 4 on page 13 of TDT Spec
	// jpb trying this version using a staged approach, only going one level at a time.

	params.put("taglength", "96");
	params.put("filter", "3");
	params.put("companyprefixlength", "7");
	String orig = "gtin=00037000302414;serial=1041970";
	String s = engine.convert(orig,
				  params,
				  LevelTypeList.BINARY);
	//               ................................................................................................
	String expect = "001100000111010000000010010000100010000000011101100010000100000000000000000011111110011000110010";
	Assert.assertEquals(expect, s);

	String s2 = engine.convert(s,
				   params,
				   LevelTypeList.TAG_ENCODING);

	expect = "urn:epc:tag:sgtin-96:3.0037000.030241.1041970";
	Assert.assertEquals(expect, s2);
	System.out.println("te " + s2);
	String s3 = engine.convert(s2,
				   params,
				   LevelTypeList.PURE_IDENTITY);
	expect = "urn:epc:id:sgtin:0037000.030241.1041970";
	Assert.assertEquals(expect, s3);
	System.out.println("pi " + s3);
	String s4 = engine.convert(s3,
				   params,
				   LevelTypeList.LEGACY);

	Assert.assertEquals(orig, s4);


    }
    public void testPage13() {
	// this test follows fig 4 on page 13 of TDT Spec

	params.put("taglength", "96");
	params.put("filter", "3");
	params.put("companyprefixlength", "7");
	String orig = "gtin=00037000302414;serial=1041970";
	String s = engine.convert(orig,
				  params,
				  LevelTypeList.BINARY);
	//               ................................................................................................
	String expect = "001100000111010000000010010000100010000000011101100010000100000000000000000011111110011000110010";
	Assert.assertEquals(expect, s);

	String s2 = engine.convert(s,
				   params,
				   LevelTypeList.LEGACY);
	Assert.assertEquals(orig, s2);


    }

    public void testPage26() {
	// table 3 on page 26 has some legacy codes. Check that they
	// convert back and forth to binary.

	params.put("taglength", "96");
	params.put("filter", "3");
	params.put("companyprefixlength", "7");
	String legacy [] =
	    {"gtin=00037000302414;serial=10419703",
	     "sscc=000370003024147856",
	     "gln=0003700030247;serial=1041970",
	     "grai=00037000302414274877906943",
	     "giai=00370003024149267890123",
	     "generalmanager=5;objectclass=17;serial=23",
	     "cageordodaac=AB123;serial=3789156"
	    };

	for (String s : legacy) {
	    String s2 = engine.convert(s,
				       params,
				       LevelTypeList.BINARY);
	    System.out.println("  " + s + " -> " + s2);
	    String s3 = engine.convert(s2,
				       params,
				       LevelTypeList.LEGACY);
	    System.out.println("    -> " + s3);
	    Assert.assertEquals(s, s3);
	}

    }
    
    public void testSgtin64() {
    	params.put("taglength", "64");
    	params.put("filter", "3");
    	params.put("companyprefixlength", "7");
    	String orig = "gtin=20073796510026;serial=1";
    	String s = engine.convert(orig,
    				  params,
    				  LevelTypeList.BINARY);
    	//               ................................................................................................
    	String expect = "1001100000010011110001111010100011110100000000000000000000000001";
    	//                  9   8   1   3   c   7   a   8   f   4   0   0   0   0   0   1
    	Assert.assertEquals(expect, s);
        }
    

    public void testUsDod96() {
	params.put("taglength", "96");
	params.put("filter", "0");
	String orig = "cageordodaac=2S194;serial=12345678901";
	String s = engine.convert(orig,
				  params,
				  LevelTypeList.BINARY);
	//               ................................................................................................
	String expect = "001011110000001000000011001001010011001100010011100100110100001011011111110111000001110000110101";
	//                  2   f   0   2   0   3   2   5   3   3   1   3   9   3   4   2   d   f   d   c   1   c   3   5
	Assert.assertEquals(expect, s);
    }

    public void testUsDod64() {
	params.put("taglength", "64");
	params.put("filter", "1");
	String orig = "cageordodaac=1D381;serial=16522293";
	String s = engine.convert(orig,
				  params,
				  LevelTypeList.BINARY);
	//               ................................................................................................
	String expect = "1100111001110001000100110011111000110001111111000001110000110101";
	//                  c   e   7   1   1   3   3   e   3   1   f   c   1   c   3   5
	Assert.assertEquals(expect, s);
    }

    public void testCSVTestSet() throws ParseException, IOException, ProcessingException {
    	
    	String testFile = "src/test/resources/TestCases1.csv";
    	File file = new File(testFile);
    	if (file.exists()) {    	
    	
    	final CSVFileProcessor fp = new CSVFileProcessor();
    	fp.processFile( testFile, new CSVFieldMapProcessor() {

    	    public void processDataLine( final int linenumber, final Map<String,String> fields ) {
    	        
    	    	System.out.println("Line: " + linenumber);
    	    	System.out.println("hex: " + fields.get("hex"));
    	    	System.out.println("urn: " + fields.get("urn"));
    	    	System.out.println("unknown: " + fields.get("unknown"));
    	    	System.out.println("status: " + fields.get("status"));
    	    	
    	    	
    	    	
    	    	// print out the field names and values
    	        // for ( final Entry field : fields.entrySet() ) {
    	            //System.out.println( String.format( "Line #%d: field: %s=%s", linenumber, field.getKey(), field.getValue() ));
    	              
    	        //}
    	    }

    	    public boolean continueProcessing()
    	    {
    	        return true;
    	    }
    	} );

    	
    	}
    	
    	
    	
    	
    	
    	
    }
    
/*
    public void testNoGEPCSpecified() throws IOException, JAXBException {
    	
    	File file = new File("target/auxiliary/ManagerTranslation.xml");
    	
        URL auxurl = file.toURL();
        System.err.println(auxurl);
    	URL schemeurl = new URL("file:\\\\C:\\Documents and Settings\\floerkem\\Desktop\\tdttrunk\\target\\classes\\schemes");
    	engine = new TDTEngine(schemeurl,schemeurl);
    	System.err.println("My code is executed");
    }
*/


}

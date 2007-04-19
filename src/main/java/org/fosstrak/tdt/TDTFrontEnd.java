/*
 * Copyright (C) 2007 University of Cambridge
 *
 * This file is part of Accada (www.accada.org).
 *
 * Accada is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 2.1, as published by the Free Software Foundation.
 *
 * Accada is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Accada; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package org.accada.tdt;

import java.util.*;

import org.accada.tdt.*;
import org.accada.tdt.types.*;

public class TDTFrontEnd { // demonstration front-end application which makes use of the TDT package

    public static final void main(String args[]) {
	try {
	    TDTEngine engine = new TDTEngine("."); // path to directory containing the subdirectories 'schemes' and 'auxiliary'

	    HashMap<String,String> extraparams = new HashMap<String, String>(); // a HashMap providing extra parameters needed in addition to the input value
		
	    extraparams.put("taglength","64"); // for inbound levels 'PURE_IDENTITY' or 'LEGACY', the taglength must be specified as "64" or "96"
	    extraparams.put("filter","1"); // for inbound levels 'PURE_IDENTITY' or 'LEGACY', the filter value must be specified - range depends on coding scheme.
	    extraparams.put("companyprefixlength","7"); // for inbound levels 'PURE_IDENTITY' or 'LEGACY', the companyprefixlength (length of the EAN.UCC Company Prefix) must be specified for GS1 coding schemes
		
	    String inbound = "gtin=00037000302414;serial=33554431";
//		String inbound = "urn:epc:id:sgtin:00370001.23456.101";
//		String inbound = "1010100000000000001000001110110001000010000011111110011000110010";
//		String inbound = "001100000101010000000010010000100010000000011101100010000100000000000000000011111110011000110010";
//		String inbound = "001100000101010000000010010000100010000000011101100010000100000000000000000011111110011000110010";
//		String inbound = "cageordodaac=1D381;serial=16522293";
//		String inbound = "cageordodaac=2S194;serial=12345678901";
//		String inhex = "2F02032533139342DFDC1C35";

		LevelTypeList outboundformat = LevelTypeList.BINARY; // permitted values are 'BINARY', 'TAG_ENCODING', 'PURE_IDENTITY', 'LEGACY' and 'ONS_HOSTNAME'
//		String inbound = engine.hex2bin(inhex);
//		System.out.println(inbin);
		
		String outbound = engine.convert(inbound, extraparams, outboundformat);
		System.out.println("Input string " + inbound);
		System.out.println("Output is "+outbound);

		String o2 = engine.convert(outbound, extraparams, LevelTypeList.TAG_ENCODING);
		System.out.println(" as TAG_ENCODING: " + o2);

		String o3 = engine.convert(outbound, extraparams, LevelTypeList.PURE_IDENTITY);
		System.out.println(" as PURE_IDENTITY: " + o3);

		String o4 = engine.convert(outbound, extraparams, LevelTypeList.LEGACY);
		System.out.println(" as LEGACY: " + o4);
		
		String o5 = engine.convert(outbound, extraparams, LevelTypeList.ONS_HOSTNAME);
		System.out.println(" as ONS_HOSTNAME: " + o5);
		
		long t = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) { 
		    String o6 = engine.convert(outbound, extraparams, LevelTypeList.PURE_IDENTITY);
		    //System.out.println(" as PURE_IDENTITY: " + o3);
		}
		System.out.println(" time to perform 100 conversions = " + (System.currentTimeMillis() - t) + " milliseconds");
		
		

		if (outboundformat.equals("BINARY")) {
		    //System.out.println("Hex is "+engine.bin2hex(outbound));
		}
		
		
//		System.out.println("bin2decstring = "+engine.bin2dec("001011011111110111000001110000110101"));
//		System.out.println("dec2bin(12345678901) = "+engine.dec2bin("12345678901"));

	}
	catch (Exception e) { 
	    e.printStackTrace(System.out);
	}
    }
	
}

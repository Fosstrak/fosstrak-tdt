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

import org.accada.tdt.TDTEngine;
import org.accada.tdt.types.*;
import junit.framework.*;

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
		engine = new TDTEngine(s);
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
}

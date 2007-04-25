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

import org.accada.tdt.PrefixTree;

import junit.framework.*;

public class TestPrefixTree extends TestCase 
{



    public void testOne() {
	
	PrefixTree<String> pt = new PrefixTree<String>();

	pt.insert("123456", "hello");
	pt.insert("131313", "there");
	pt.insert("123456", "funny");
	
	List<String> expect = (List<String>) Arrays.asList(new String [] {
	    "hello", "funny" });

	List<String> test = pt.search("123456789");
	Assert.assertEquals(expect, test);

	expect = Arrays.asList(new String [] {
	    "there" });

	test = pt.search("13131313");
	Assert.assertEquals(expect, test);

	expect = Arrays.asList(new String [0] );
	test = pt.search("15131313");
	Assert.assertEquals(expect, test);

	

    }	
}

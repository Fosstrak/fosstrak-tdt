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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * Entry in the GEPC64 table, mapping an index to a company prefix. Class is
 * annotated to be used by JAXB for unmarshalling.
 * 
 * @author Jochen Mader - jochen@pramari.com
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "entry")
public class Entry {
	/** Field _index */
	@XmlAttribute
	private int index;
	/** Field _companyPrefix */
	@XmlAttribute
	private String companyPrefix;

	/**
	 * Returns the value of field 'companyPrefix'.
	 * 
	 * @return String
	 * @return the value of field 'companyPrefix'.
	 */
	public String getCompanyPrefix() {
		return this.companyPrefix;
	}

	/**
	 * Returns the value of field 'index'.
	 * 
	 * @return int
	 * @return the value of field 'index'.
	 */
	public int getIndex() {
		return this.index;
	}

	/**
	 * Sets the value of field 'companyPrefix'.
	 * 
	 * @param companyPrefix
	 *            the value of field 'companyPrefix'.
	 */
	public void setCompanyPrefix(String companyPrefix) {
		this.companyPrefix = companyPrefix;
	}

	/**
	 * Sets the value of field 'index'.
	 * 
	 * @param index
	 *            the value of field 'index'.
	 */
	public void setIndex(int index) {
		this.index = index;
	}

}

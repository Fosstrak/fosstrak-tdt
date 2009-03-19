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

import java.util.ArrayList;
import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;



/**
 * This class is used to represent a GEPC64Table. Class is annotated to be used
 * by JAXB for unmarshalling.
 * 
 * @author Jochen Mader - jochen@pramari.com
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GEPC64Table", propOrder = { "entry" })
public class GEPC64Table {
	/** Field _date */
	@XmlAttribute(required = true)
	private Date date;
	/** Field _entryList */
	@XmlElement(required = true)
	private ArrayList<Entry> entry;

	public GEPC64Table() {
		entry = new ArrayList<Entry>();
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public ArrayList<Entry> getEntryList() {
		return entry;
	}

	public void setEntryList(ArrayList<Entry> entryList) {
		this.entry = entryList;
	}

}

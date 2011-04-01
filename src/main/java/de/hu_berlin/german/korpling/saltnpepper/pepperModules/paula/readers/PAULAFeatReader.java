/**
 * Copyright 2009 Humboldt University of Berlin, INRIA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */
package de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.readers;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *This reader reads a paula file which is compatible to paula_mark.dtd.
 * 
 * @author Florian Zipser
 * @version 1.0
 */
public class PAULAFeatReader extends PAULASpecificReader
{
//	 --------------------------- SAX mezhods ---------------------------
	/**
	 * stores string, which identifies feats of document or corpus
	 */
	private static final String KW_ANNO= "anno";
	private static final String KW_ANNO_FEAT= "annoFeat"; 
	
	/**
	 * Stores if feats refers to a document or corpus
	 */
	private Boolean isMetaFeat= false;
	/**
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(	String uri,
            					String localName,
            					String qName,
            					Attributes attributes) throws SAXException
    {
		{//calls super-class for setting paula-id, paula-type and xml-base
			super.startElement(uri, localName, qName, attributes);
		}//calls super-class for setting paula-id, paula-type and xml-base
		//FEAT-element found
		if (this.isTAGorAttribute(qName, TAG_FEAT_FEATLIST))
		{
			if (	(this.getXmlBase()!= null)&&
					(!this.getXmlBase().equalsIgnoreCase("")))
			{	
				String parts[]= this.getXmlBase().split("[.]");
				if (	(parts.length>= 2)&&
						(parts[parts.length-2].equalsIgnoreCase(KW_ANNO)))
				{
					this.isMetaFeat= true;
				}
			}
		}
		else if (this.isTAGorAttribute(qName, TAG_FEAT_FEAT))
		{//FEAT-element found
			String featID= null;	//feat.id
			String featHref= null;	//feat.href
			String featTar= null;	//feat.target
			String featVal= null;	//feat.value
			String featDesc= null;	//feat.description
			String featExp= null;	//feat.example
			
			for(int i= 0; i < attributes.getLength(); i++)
			{	
				//Attribut FEAT.ID gefunden
				if (this.isTAGorAttribute(attributes.getQName(i), ATT_FEAT_FEAT_ID))
					featID= attributes.getValue(i);
				//Attribut FEAT.HREF gefunden
				else if (this.isTAGorAttribute(attributes.getQName(i), ATT_FEAT_FEAT_HREF))
					featHref= attributes.getValue(i);
				//Attribut FEAT.TARGET gefunden
				else if (this.isTAGorAttribute(attributes.getQName(i), ATT_FEAT_FEAT_TAR))
					featTar= attributes.getValue(i);
				//Attribut FEAT.VALUE gefunden
				else if (this.isTAGorAttribute(attributes.getQName(i), ATT_FEAT_FEAT_VAL))
					featVal= attributes.getValue(i);
				//Attribut FEAT.DESCRIPTION gefunden
				else if (this.isTAGorAttribute(attributes.getQName(i), ATT_FEAT_FEAT_DESC))
					featDesc= attributes.getValue(i);
				//Attribut FEAT.EXAMPLE gefunden
				else if (this.isTAGorAttribute(attributes.getQName(i), ATT_FEAT_FEAT_EXP))
					featExp= attributes.getValue(i);
			}
			
			{//checking if href contains a new not already read file
				this.checkForFileReference(featHref);
				this.checkForFileReference(featTar);
			}//checking if href contains a new not already read file
			
			if (this.getPaulaType().equalsIgnoreCase(KW_ANNO_FEAT))
			{//file is annofeat, do nothing
				
			}//file is annofeat, do nothing
			else if (this.isMetaFeat)
			{//callback for mapper in case of feat means corpus or document 
				this.getMapper().paulaFEAT_METAConnector(this.getPaulaFile(), this.getPaulaID(), this.getPaulaType(), this.getXmlBase(), featID, featHref, featTar, featVal, featDesc, featExp);
			}//callback for mapper in case of feat means corpus or document
			else if (	(	(featVal== null)	||
						(featVal.equalsIgnoreCase(""))) &&
					(	(featTar!= null) &&
							(!featTar.equalsIgnoreCase(""))))
			{//callback for mapper for feat misused as rel
				this.getMapper().paulaRELConnector(this.getPaulaFile(), this.getPaulaID(), this.getPaulaType(), this.getXmlBase(), featID, featHref, featTar);
			}//callback for mapper for feat misused as rel
			else
			{//callback for mapper for normal feat
				this.getMapper().paulaFEATConnector(this.getPaulaFile(), this.getPaulaID(), this.getPaulaType(), this.getXmlBase(), featID, featHref, featTar, featVal, featDesc, featExp);
			}//callback for mapper for normal feat
		}
    }
	
	/**
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(	String uri,
            				String localName,
            				String qName) throws SAXException
    {
	}
}

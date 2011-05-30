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
package de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.osgi.service.log.LogService;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.SAXException;

import de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.exceptions.PAULAExporterException;
import de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.PAULAXMLStructure;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.modules.SDocumentStructureAccessor;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SCorpusGraph;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SDocument;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SDocumentGraph;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SSpan;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SStructure;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SStructuredNode;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.STextualDS;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.STextualRelation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SToken;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SAnnotation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SElementId;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SLayer;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SNode;

/**
 * Maps SCorpusGraph objects to a folder structure and maps a SDocumentStructure to the necessary files containing the document data in PAULA notation.
 * @author Mario Frank
 *
 */
public class Salt2PAULAMapper implements PAULAXMLStructure
{
	/**
	 * OSGI-log service
	 */
	private LogService logService= null;
	public void setLogService(LogService logService) {
		this.logService = logService;
	}

	public LogService getLogService() {
		return logService;
	}
	
	
	/**
	 * 	Maps the SCorpusStructure to a folder structure on disk relative to the given corpusPath.
	 * @param sCorpusGraph
	 * @param corpusPath
	 * @return null, if no document directory could be created <br>
	 * 		   HashTable&lt;SElementId,URI&gt; else.<br>
	 * 			Comment: URI is the complete document path
	 */
	public Hashtable<SElementId, URI> mapCorpusStructure(SCorpusGraph sCorpusGraph, 
														URI corpusPath)
	{   
		if (sCorpusGraph== null)
			throw new PAULAExporterException("Cannot export corpus structure, because sCorpusGraph is null.");
		if (corpusPath== null)
			throw new PAULAExporterException("Cannot export corpus structure, because the path to export to is null.");
		Hashtable<SElementId, URI> retVal= null;
		int numberOfCreatedDirectories = 0;
		//System.out.println(corpusPath.toFileString());
		
		List<SDocument> sDocumentList =  Collections.synchronizedList(sCorpusGraph.getSDocuments());
		
		Hashtable<SElementId,URI> tempRetVal = new Hashtable<SElementId,URI>();
		
		// Check whether corpus path ends with Path separator. If not, hang it on, else convert it to String as it is
		String corpusPathString = corpusPath.toFileString().replace("//", "/");
		if (! corpusPathString.endsWith(File.separator)){
			corpusPathString = corpusPathString.concat(File.separator);
		} else {
			corpusPathString = corpusPath.toFileString();
		}
		for (SDocument sDocument : sDocumentList) {
			String completeDocumentPath = corpusPathString;
			String relativeDocumentPath;
			// Check whether sDocumentPath begins with a salt:/. If it does, remove it and save the remainder. else just save the complete String
			relativeDocumentPath = sDocument.getSElementId().getValueString().replace("salt:/", "");
			// remove leading path separator, if existent
			if (relativeDocumentPath.substring(0, 1).equals(File.pathSeparator)){
				completeDocumentPath = completeDocumentPath.concat(relativeDocumentPath.substring(1));
			} else {
				completeDocumentPath = completeDocumentPath.concat(relativeDocumentPath);
			}
				
			// Check whether directory exists and throw an exception if it does. Else create it
			// We don't need this... we just overwrite the document
			if ((new File(completeDocumentPath).isDirectory())){
				numberOfCreatedDirectories++;
				tempRetVal.put(sDocument.getSElementId(),org.eclipse.emf.common.util.URI.createFileURI(completeDocumentPath));
			} else {
				if (!( (new File(completeDocumentPath)).mkdirs() )){ 
					throw new PAULAExporterException("Cannot create directory "+completeDocumentPath);
				} else {
					numberOfCreatedDirectories++;
					tempRetVal.put(sDocument.getSElementId(),org.eclipse.emf.common.util.URI.createFileURI(completeDocumentPath));
				}
			}
		}
		//System.out.println("Created directories (number): "+ numberOfCreatedDirectories);
		if (numberOfCreatedDirectories > 0){
			retVal = tempRetVal;
		}
		tempRetVal = null;
		return(retVal);
	}

	/**
	 * 	Maps the SDocument to PAULA format and writes files to documentPath.
	 * @param sDocument the Salt document that has to be mapped
	 * @param documentPath the output document path to map to
	 * @return nothing
	 */
	public void mapSDocumentStructure(SDocument sDocument, URI documentPath)
	{
		if (sDocument == null)
			throw new PAULAExporterException("Cannot export document structure because sDocument is null");
		
		if (documentPath == null)
			throw new PAULAExporterException("Cannot export document structure because documentPath is null");
		
		EList<STextualDS> sTextualDataSources = sDocument.getSDocumentGraph().getSTextualDSs();
		// create a Hashtable(SName,{PrintWriter,URI}) with initial Size equal to the number of Datasources 
		Hashtable<String,Object[]> fileTable = new Hashtable <String,Object[]>(sTextualDataSources.size());
		
		
		String documentName = sDocument.getSName();
		
		mapTextualDataSources(fileTable,sTextualDataSources,documentName,documentPath,false);
		//mapTokens(sDocument.getSDocumentGraph().getSTextualRelations(),fileTable , docID,documentPath,false);
		//String oneDS = sTextualDataSources.get(0).getSId();
		//mapLayersToFiles(sDocument.getSDocumentGraph(),fileTable,docID,documentPath.toFileString(), oneDS);
		mapLayers(sDocument.getSDocumentGraph(), documentPath, documentName, fileTable);
		
	}

	/**
	 * Writes the primary text sText to a file "documentID_text.xml" in the documentPath
	 * and returns the URI (filename).
	 * @param fileTable TODO
	 * @param sTextualDS the primary text
	 * @param documentID the document id
	 * @param documentPath the document path
	 */
	
		
	private void mapTextualDataSources( Hashtable<String,Object[]> fileTable, 
									  EList<STextualDS> sTextualDS, 
									  String documentID,
									  URI documentPath, boolean validate) {
		if (fileTable == null)
			throw new PAULAExporterException("Cannot map Data Sources because fileTable is null");

		if (sTextualDS.isEmpty())
			throw new PAULAExporterException("Cannot map Data Sources because there are none");
		if (documentID.equals(""))
			throw new PAULAExporterException("Cannot map Data Sources because documentID is empty (\"\")");
		if (documentPath == null)
			throw new PAULAExporterException("Cannot map Data Sources because documentPath is null");
		
		File textFile;
		int dsNum = sTextualDS.size();
		for (STextualDS sText : sTextualDS){
			if (dsNum == 1){
				textFile = new File(documentPath.toFileString()+ 
								File.separator + documentID+ ".merged.text.xml");
			} else {
				textFile = new File(
								documentPath.toFileString()+ 
								File.separator + documentID +".merged.text."+
								(sTextualDS.indexOf(sText)+1)+".xml");
			}
			//System.out.println("SText "+sText.getSName());
			try{
				if (! textFile.createNewFile())
					System.out.println("File: "+ textFile.getName()+ " already exists");
			
				PrintWriter output = new PrintWriter( 
										new BufferedWriter(
											new OutputStreamWriter(
												new FileOutputStream(
														textFile)
												,"UTF8")),false);
			
				fileTable.put(sText.getSId(),new Object[] {output,URI.createFileURI(textFile.getAbsolutePath())});	
				
				{
					output.println(TAG_HEADER_XML);
					output.println(PAULA_TEXT_DOCTYPE_TAG);
					output.println(TAG_PAULA_OPEN);
					output.println( new StringBuffer("\t<header paula_id=\"pepper.")
									.append(documentID)
									.append("_text\" type=\"text\"/>").toString());
					output.println("\t"+TAG_TEXT_BODY_OPEN);
					output.println("\t\t" + sText.getSText());
					output.println("\t"+TAG_TEXT_BODY_CLOSE);
					output.println(PAULA_CLOSE_TAG);			
				}	
				output.close();
				// dispose PrintWriter
				fileTable.get(sText.getSId())[0] = null;
				if (validate)
				  System.out.println( "File " + textFile.getName() + " is valid: " + this.isValidXML(textFile) );
				
				}catch (IOException e){
					System.out.println("Exception: "+ e.getMessage());
				}
			
							
		}
		
		// We don't write the output text to a variable to have less overhead
		// since java used calls by value 
		// The DTD needs a leading letter or underscore in the attribute value of
		// paula_id. Thus, I introduced a prefix (pepper)
		
	}
	
	
	
	
	/**
	 * Port for refactoring: map layer by layer
	 * @param sDocumentGraph
	 * @param documentPath
	 * @param documentId
	 * @param fileTable
	 */
	private void mapLayers(SDocumentGraph sDocumentGraph,
						   URI documentPath,
						   String documentId,
						   Hashtable<String,Object[]> fileTable){
		
		if (sDocumentGraph == null)
			throw new PAULAExporterException("Cannot map Layers files because document graph is null");
		if (documentPath.equals(""))
			throw new PAULAExporterException("Cannot map Layers because documentPath is empty (\"\")");
		if (documentId.equals(""))
			throw new PAULAExporterException("Cannot map Layers files because documentID is empty (\"\")");
		if (fileTable == null)
			throw new PAULAExporterException("Cannot map Layers files because fileTable is null");
	
		SDocumentStructureAccessor accessor = new SDocumentStructureAccessor();
		accessor.setSDocumentGraph(sDocumentGraph);
		
		EList<SSpan> spanList = new BasicEList<SSpan>(sDocumentGraph.getSSpans());
		EList<SStructure> structList = new BasicEList<SStructure>(sDocumentGraph.getSStructures());
		
		EList<SSpan> layerSpanList = new BasicEList<SSpan>();
		EList<SStructure> layerStructList = new BasicEList<SStructure>();
		EList<SToken> layerTokenList = new BasicEList<SToken>();
		EList<SSpan> multiLayerSpanList = new BasicEList<SSpan>();
		EList<SStructure> multiLayerStructList = new BasicEList<SStructure>();
		EList<SToken> multiLayerTokenList = new BasicEList<SToken>();
		
		for (SLayer layer : sDocumentGraph.getSLayers()){
			layerSpanList = new BasicEList<SSpan>();
			layerStructList = new BasicEList<SStructure>();
			layerTokenList = new BasicEList<SToken>();
			
			for (SNode sNode : layer.getSNodes()){
				if (sNode instanceof SToken){
					if (sNode.getSLayers().size() > 1){
						multiLayerTokenList.add((SToken)sNode);
					} else {
						layerTokenList.add((SToken)sNode);
						
					}
				}
				
				if (sNode instanceof SSpan ){
					if (sNode.getSLayers().size() > 1){
						multiLayerSpanList.add((SSpan) sNode);
					} else {
						spanList.remove((SSpan) sNode);
						layerSpanList.add((SSpan) sNode);
					}
				}
				
				if (sNode instanceof SStructure ){
					if (sNode.getSLayers().size() > 1){
						multiLayerStructList.add((SStructure) sNode);
					} else {
						structList.remove((SStructure) sNode);
						layerStructList.add((SStructure) sNode);
					}
					
				}
					
				
			}
			
			if (! layerTokenList.isEmpty()){
				if (sDocumentGraph.getSTextualRelations().size() > layerTokenList.size())
					throw new PAULAExporterException("Salt2PAULAMapper: There are more Textual Relations then Token in layer"+ layer.getSName());
						
				mapTokens(sDocumentGraph.getSTextualRelations(),fileTable,documentId,documentPath);
			}
			if (! layerSpanList.isEmpty()){
				mapSpans(layerSpanList,fileTable,documentId,documentPath);
			}
			if (! layerStructList.isEmpty()){
				mapStructs(layerStructList,fileTable,documentId,documentPath);
			}
		}
		if (! spanList.isEmpty()){
			
		}
		if (! structList.isEmpty()){
			
		}
		
	}
	
	
	
	/**
	 * Extracts the tokens including the xPointer from the STextualRelation list 
	 * and writes them to files "documentID.tokenfilenumber.tok.xml" in the documentPath.
	 * 
	 * Seems to work, but needs test and more documentation
	 * 
	 * @param sTextRels list of textual relations (tokens)pointing to a target (data source)
	 * @param sIdMap Hashmap including the SId (String) of the data-source, the URI of the corresponding textfile and a PrintWriter for each token file
	 * @param documentID the document id of the Salt document
	 * @param documentPath the path to which the token files will be mapped
	 */
	private void mapTokens( EList<STextualRelation> sTextRels,
									   Hashtable<String, Object[]> sIdMap, 
									   String documentID,  
									   URI documentPath) {
		
		if (sTextRels.isEmpty())
			throw new PAULAExporterException("Cannot create token files because there are no textual relations");
		if (documentID.equals(""))
			throw new PAULAExporterException("Cannot create token files because documentID is empty (\"\")");
		if (documentPath == null)
			throw new PAULAExporterException("Cannot create token files because documentPath is null");
		if (sIdMap == null)
			throw new PAULAExporterException("Cannot create token files because no textFileTable is defined" );
		
		String baseTextFile;
		int tokenFileIndex = 0;
		File tokenFile = null;
		
		StringBuffer fileString = new StringBuffer();
		for (STextualRelation sTextualRelation : sTextRels){
			String sTextDSSid = sTextualRelation.getSTarget().getSId();
			String paulaMarkTag = new StringBuffer("\t\t<mark id=\"")
				  .append(sTextualRelation.getSToken().getSName()).append("\" ")
			      .append("xlink:href=\"#xpointer(string-range(//body,'',")
			      .append(sTextualRelation.getSStart()+1).append(",")
			      .append(sTextualRelation.getSEnd()-sTextualRelation.getSStart())
			      .append("))\" />").toString();
			
			if (sIdMap.get(sTextDSSid)[0] != null){
				((PrintWriter)(sIdMap.get(sTextDSSid)[0])).println(paulaMarkTag);
		
			} else {
				try {
				String fileName = new String(((URI)(sIdMap.get(sTextDSSid))[1]).toFileString().replace("text", "tok"));
				tokenFile = new File(fileName);
				if ( ! tokenFile.createNewFile())
					System.out.println("File: "+ tokenFile.getName()+ " already exists");
				
				
				sIdMap.get(sTextDSSid)[0] = new PrintWriter(new BufferedWriter(	
												new OutputStreamWriter(
													new FileOutputStream(tokenFile),
													"UTF8")),
													true);
				
				baseTextFile = tokenFile.getName().replace("tok", "text");
				sIdMap.get(sTextDSSid)[1] = URI.createFileURI(tokenFile.getAbsolutePath());
				
				fileString.append(TAG_HEADER_XML).append(LINE_SEPARATOR)
				  .append(PAULA_MARK_DOCTYPE_TAG).append(LINE_SEPARATOR)
				  .append(TAG_PAULA_OPEN).append(LINE_SEPARATOR)
				  // appending token header
				  .append("\t<header paula_id=\"pepper.").append(documentID)
				  .append(".").append(tokenFileIndex).append("_tok\"/>").append(LINE_SEPARATOR)
				  // append markList open Tag
				  .append("\t<markList xmlns:xlink=\"http://www.w3.org/1999/xlink\" type=\"tok\" xml:base=\"")
				  .append(baseTextFile.replace(tokenFile.getPath(),""))
				  .append("\">").append(LINE_SEPARATOR)
				  .append(paulaMarkTag).append(LINE_SEPARATOR);
				
				((PrintWriter) (sIdMap.get(sTextDSSid)[0])).write(fileString.toString());
				
				fileString.delete(0, fileString.length()+1);
				} catch (IOException e){
					System.out.println("Exception: "+ e.getMessage());
				} catch (SecurityException e) {
					System.out.println("SecurityException: "+ e.getMessage());
				}
			}
		}
		
		for (Object[] writer :  (sIdMap.values())){
			((PrintWriter) writer[0] ).write(PAULA_TOKEN_FILE_CLOSING);
			((PrintWriter) writer[0] ).close();
			fileString.delete(0, fileString.length()+1);
		}
	}
	
	
	private void mapSpans(EList<SSpan> layerSpanList, Hashtable<String, Object[]> fileTable, String documentId, URI documentPath){
		
	}
	
	
	private void mapStructs(EList<SStructure> layerStructList, Hashtable<String, Object[]> fileTable, String documentId, URI documentPath) {
		// TODO Auto-generated method stub
		
	}

	private void mapAnnotations(){
		
	}
	
	/**
	 * 
	 * @param graph
	 * @param sIdMap
	 * @param docID
	 * @param documentPath
	 */
	private void mapLayersToFiles(SDocumentGraph graph, Hashtable<String,Object[]> sIdMap ,String docID, String documentPath, String firstDSName) {
		if (documentPath.equals(""))
			throw new PAULAExporterException("Cannot map Layers because documentPath is empty (\"\")");
		if (docID.equals(""))
			throw new PAULAExporterException("Cannot map Layers files because documentID is empty (\"\")");
		if (graph == null)
			throw new PAULAExporterException("Cannot map Layers files because document graph is null");
		
		SDocumentStructureAccessor accessor = new SDocumentStructureAccessor();
		accessor.setSDocumentGraph(graph);
		
		String markTag = "";
		Hashtable<String,PrintWriter> fileTable = new Hashtable<String,PrintWriter>();
		File f;
		EList<SSpan> spanList = new BasicEList<SSpan>(graph.getSSpans());
		EList<SStructure> structList = new BasicEList<SStructure>(graph.getSStructures());
		EList<String> spanFileNames = new BasicEList<String>();
		EList<String> structFileNames = new BasicEList<String>();
		StringBuffer lineToWrite = new StringBuffer();
		EList<SToken> overlappingTokens = null;
		
		int dsNum = graph.getSTextualDSs().size();
		String baseMarkFile = ((URI)sIdMap.get(firstDSName)[1]).toFileString()
		.substring(((URI)sIdMap.get(firstDSName)[1])
			.toFileString().lastIndexOf(File.separator)+1);
		
		
		for (SLayer layer : graph.getSLayers()){
			
			//System.out.println("Superlayers: "+layer.getSuperLayer());
			String fileToWrite="";
			String spanFileToWrite = docID +"."+layer.getSName() +".mark.xml";
			String structFileToWrite = docID +"."+ layer.getSName() +".struct.xml";
			String subLayer = "";
			String sTextName = "";
			//if (layer.getSSubLayers().size() > 0)
			//	subLayer = layer.getSSubLayers().get(0).getSName();
			
			//System.out.println("Layer: " + layer.getSName() +" Sublayer"+subLayer);
			//System.out.println("Layer anno: "+layer.getSAnnotations().get(0).getSName());
			/**
			 * Iterate over all layers and map the nodes to mark/struct
			 */
			for (SNode sNode : layer.getSNodes() ){
				
				if (sNode instanceof SSpan){
					try{
					overlappingTokens = accessor.getSTextualOverlappedTokens((SStructuredNode) sNode);
					markTag = createMarkTag(
							sNode.getSName(),
							sIdMap, overlappingTokens, 
							dsNum,
							baseMarkFile,
							firstDSName
							)
							+ LINE_SEPARATOR;
					if (fileTable.get(spanFileToWrite) == null){
						if (!(f = new File(documentPath + File.separator + spanFileToWrite))
								.createNewFile())
							System.out.println("File: "+ f.getName()+ " already exists");
					
						fileTable.put(spanFileToWrite, new PrintWriter(
							new BufferedWriter(	
									new OutputStreamWriter(
									new FileOutputStream(f.getAbsoluteFile())
									,"UTF8")),
											false));
					
						//System.out.println(sNode.getLayers().get(0));
						fileTable.get(spanFileToWrite).write(
							createMarkFileBeginning(
									spanFileToWrite.substring(0, spanFileToWrite.length()-4),
									"mark",
									baseMarkFile, dsNum));
					
						spanList.remove((SSpan)sNode);
						spanFileNames.add(spanFileToWrite);
						fileTable.get(spanFileToWrite)
							.write(
								createMarkTag(
									sNode.getSName(),
									sIdMap, overlappingTokens, 
									dsNum,
									baseMarkFile,
									firstDSName)
									+ LINE_SEPARATOR);
					
						fileToWrite = docID +"."+ layer.getSName()+ ".mark";
					} else {
						fileTable.get(spanFileToWrite)
						.write(
							createMarkTag(sNode.getSName(),
									sIdMap, overlappingTokens,dsNum, 
									baseMarkFile,firstDSName )
									+ LINE_SEPARATOR);
					}
					}catch(IOException ioe){
						throw new PAULAExporterException("mapLayersToFiles: Could not write File "+spanFileToWrite.toString()+": "+ioe.getMessage());
					}
				} else {
					if (sNode instanceof SStructure){
						structList.remove((SStructure) sNode);	
						
						try {
						if (fileTable.get(structFileToWrite) == null){	
							if (! (f = new File(documentPath + File.separator +structFileToWrite)).createNewFile())
								System.out.println("File: "+ f.getName()+ " already exists");
						
							fileTable.put(structFileToWrite, new PrintWriter(
								new BufferedWriter(	
										new OutputStreamWriter(
										new FileOutputStream(f.getAbsoluteFile())
										,"UTF8")),
												false));
							structFileNames.add(structFileToWrite);
							fileTable.get(structFileToWrite).write(createStructFileBeginning(
								structFileToWrite.replace(".xml", ""),
								"struct","struct"));
						} else {
							fileTable.get(structFileToWrite).println(sNode.getSName());
						}
						} catch(IOException ioe){
							throw new PAULAExporterException("mapLayersToFiles: Could not write File "+structFileToWrite.toString()+": "+ioe.getMessage());
						}
						
						
						fileToWrite = docID +"."+ layer.getSName()+ ".struct";
					} else {
						if (sNode instanceof SToken)
							sTextName = ((SToken)sNode).getSDocumentGraph()
												.getSTextualRelations().get(
												((SToken)sNode).getSDocumentGraph().getSTextualRelations()
												.indexOf(sNode)).getSTarget().getSName();
							fileToWrite = ((URI)sIdMap.get( sTextName)[1]).toFileString()
										  .substring(((URI)sIdMap.get( sTextName)[1]).toFileString().lastIndexOf(File.separator + 1));
										
						
					}
				}
				
				for (SAnnotation sAnnotation : sNode.getSAnnotations()){
					String qName = fileToWrite + "_"+sAnnotation.getQName();
					if (fileTable.containsKey(qName)){
						fileTable.get(qName).println(lineToWrite.toString());
					} else {
						f = new File(documentPath + File.separator+qName+".xml");
						try {
							fileTable.put(f.getAbsolutePath(), 
									  new PrintWriter(
									  new BufferedWriter(	
									  new OutputStreamWriter(
									  new FileOutputStream(f.getAbsoluteFile()),"UTF8")),
														true));
							fileTable.get(f.getAbsolutePath()).write(
									(new StringBuffer("Anno: "+sAnnotation.getName())).toString());
						 
						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					/*
					 * schreibe SAnnotation in feat file fuer alle SAnnotation mit 
					 * gleichem SAnnotation.getSFullName (oder getSQName(), 
					 * hab ich vergessen)
					 * Achtung: Referenzfile muss immer das gleiche sein, 
					 * sonst neue Datei
            		 */
				}
				
			}
		
		}
		for (String spanFileName : spanFileNames)
			fileTable.get(spanFileName).write("\t"+MARK_LIST_CLOSE_TAG+LINE_SEPARATOR+PAULA_CLOSE_TAG);
			
		
		for (PrintWriter out : fileTable.values()){
			
			out.close();
		}
		/**
		 * Map spans and structures without Layer to files
		 */
		if (! spanList.isEmpty()){
			
		}
		if (! structList.isEmpty()){
			
		}
		
		/*
		 * 
    	   

			wenn SDocumentGrpah.getSSPans().size() != numOfSSPan
    			suche alle SSPans ohne Layer und wieder hole obiges
			wenn SDocumentGrpah.getSStructure().size() != numOfSStructure
    			suche alle SSPans ohne Layer und wieder hole obiges 
		 */
	}
	
	
	
	
	/**
	 * TO DO: Wenn das Token nicht auf die Base zeigt, Base davorhaengen!!!!
	 * @param sName
	 * @param eList
	 * @param dataSourceCount
	 * @param base
	 * @param firstDSName 
	 * @return
	 */

	
	private String createMarkTag(String sName, Hashtable<String,Object[]> sIdMap, EList<SToken> eList,int dataSourceCount, String base, String firstDSName) {
		StringBuffer buffer = new StringBuffer();
		EList<STextualRelation> rel = eList.get(0).getSDocumentGraph().getSTextualRelations();
		String sTextualDSName;
		String tokenFile;
		URI tokenPath;
		buffer.append("\t\t<mark ").append(ATT_MARK_MARK_ID).append("=\"").append(sName)
			.append("\" ").append(ATT_MARK_MARK_HREF).append("=\"(");
		if (dataSourceCount == 1){
			for (SToken token : eList){
				if (eList.indexOf(token) < eList.size()-1){
					buffer.append("#").append(token.getSName()).append(",");
				} else {
				buffer.append("#").append(token.getSName());
				}
			}
		} else {
			for (SToken token : eList){
				sTextualDSName = rel.get(rel.indexOf(token)).getSTarget().getSName();
				tokenPath = (URI)sIdMap.get(sTextualDSName)[1];
				tokenFile = tokenPath.toFileString().substring(tokenPath.toFileString().lastIndexOf(File.separator+1));
				if (eList.indexOf(token) < eList.size()-1){
					if (sTextualDSName.equals(firstDSName)){
						buffer.append("#").append(token.getSName()).append(",");
					} else {
						buffer.append(tokenFile).append("#").append(token.getSName()).append(",");
					}
				} else {
					if (sTextualDSName.equals(firstDSName)){
						buffer.append("#").append(token.getSName()).append(",");
					} else {
						buffer.append(tokenFile).append("#").append(token.getSName());
					}
				}
			}
		}
		buffer.append(")\"/>");
		return buffer.toString();
	}

	private String createStructFileBeginning(String replace, String string,
			String string2) {
		// TODO Auto-generated method stub
		return "";
	}

	/**
	 * Method for construction of the Mark file preamble (Headers and MarkList Tag)
	 * 
	 * @param paulaID
	 * @param type 
	 * @param base base token file (the first if there is more then one)
	 * @param dsNum Count of textual datasources
	 * @return String representation of the Preamble
	 */
	private String createMarkFileBeginning(String paulaID,String type, String base, int dsNum) {
		
		StringBuffer buffer = new StringBuffer(TAG_HEADER_XML);
		buffer.append(LINE_SEPARATOR).append(PAULA_MARK_DOCTYPE_TAG)
			  .append(LINE_SEPARATOR).append(TAG_PAULA_OPEN)
			  .append(LINE_SEPARATOR).append("\t")
			  .append("<"+TAG_HEADER+" "+ATT_HEADER_PAULA_ID[0]+"=\""+paulaID+"\"/>")
			  .append(LINE_SEPARATOR).append("\t")
			  .append("<"+TAG_MARK_MARKLIST+" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "+
					  ATT_MARK_MARKLIST_TYPE+"=\""+type+"\" "+ATT_MARK_MARKLIST_BASE+"=\""+base+"\" >")
			  .append(LINE_SEPARATOR);
		return buffer.toString();
	}

	
	
	/**
	 * Checks whether the file is valid by the DTD which is noted in the file
	 * 
	 * @param fileToValidate
	 * @return true if the fileToValidate matches the specified DTD
	 * 			false else
	 */
	private boolean isValidXML(File fileToValidate) {
				
		try {
	      	 File XMLFile = fileToValidate;
	      	 
	         DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
	         documentBuilderFactory.setValidating(true); 
	         DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
	         ErrorHandler errorHandler = new DefaultHandler();
	         documentBuilder.setErrorHandler(errorHandler);
	         Document document = documentBuilder.parse(XMLFile);
	         
		  } catch (ParserConfigurationException e) {
	         System.out.println(e.getMessage()); 
	         return false;
	      } catch (SAXException e) {
	         System.out.println(e.getMessage());
	         return false;
	      } catch (IOException e) {
	         System.out.println(e.getMessage());
	      }
	      return true;	 

		
	}
	
	
	
	  
}	
	


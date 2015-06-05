/**
 * Copyright 2009 Humboldt-Universität zu Berlin, INRIA.
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.rowset.spi.XmlWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import de.hu_berlin.german.korpling.saltnpepper.pepper.common.DOCUMENT_STATUS;
import de.hu_berlin.german.korpling.saltnpepper.pepper.modules.PepperImporter;
import de.hu_berlin.german.korpling.saltnpepper.pepper.modules.PepperModule;
import de.hu_berlin.german.korpling.saltnpepper.pepper.modules.exceptions.PepperModuleException;
import de.hu_berlin.german.korpling.saltnpepper.pepper.modules.impl.PepperMapperImpl;
import de.hu_berlin.german.korpling.saltnpepper.pepper.util.XMLStreamWriter;
import de.hu_berlin.german.korpling.saltnpepper.salt.graph.Edge;
import de.hu_berlin.german.korpling.saltnpepper.salt.graph.Label;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SAudioDSRelation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SAudioDataSource;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SDominanceRelation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SPointingRelation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SSpan;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SStructure;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.STYPE_NAME;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.STextualDS;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.STextualRelation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SToken;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SAnnotatableElement;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SAnnotation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SIdentifiableElement;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SLayer;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SMetaAnnotatableElement;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SMetaAnnotation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SNode;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SRelation;

/**
 * Maps SCorpusGraph objects to a folder structure and maps a SDocumentStructure
 * to the necessary files containing the document data in PAULA notation.
 * 
 * @author Mario Frank
 * @author Florian Zipser
 */

public class Salt2PAULAMapper extends PepperMapperImpl implements PAULAXMLDictionary, FilenameFilter {
	private static final Logger logger = LoggerFactory.getLogger(PAULAExporter.MODULE_NAME);

	private URI resourcePath = null;

	/** Returns the path to the location of additional resources. **/
	public URI getResourcePath() {
		return resourcePath;
	}

	/**
	 * Method for setting a reference to the path where the resources for the
	 * PAULAExporter (e.g. DTD-files) are located.
	 * 
	 * @param resources
	 */
	public void setResourcePath(URI resources) {
		resourcePath = resources;
	}

	/**
	 * Implementation for FilenameFilter. This is needed for fetching only the
	 * DTD-files from resource path for copying to output folder.
	 */
	public boolean accept(File f, String s) {
		return s.toLowerCase().endsWith(".dtd");
	}

	public static final String PATH_DTD = "dtd_11/";

	/**
	 * Maps {@link SMetaAnnotation} obejcts.
	 */
	@Override
	public DOCUMENT_STATUS mapSCorpus() {
		mapSMetaAnnotations(getSDocument());
		return (DOCUMENT_STATUS.COMPLETED);
	}

	/**
	 * {@inheritDoc PepperMapper#setSDocument(SDocument)}
	 * 
	 * OVERRIDE THIS METHOD FOR CUSTOMIZED MAPPING.
	 */
	@Override
	public DOCUMENT_STATUS mapSDocument() {
		if (getSDocument() == null)
			throw new PepperModuleException(this, "Cannot export document structure because sDocument is null");
		if (getSDocument().getSDocumentGraph() == null) {
			throw new PepperModuleException(this, "Cannot export document structure because sDocumentGraph is null");
		}
		if (this.getResourceURI() == null){
			throw new PepperModuleException(this, "Cannot export document structure because documentPath is null for '" + this.getSDocument().getSElementId() + "'.");
		}
		// copy DTD-files to output-path
		if (getResourcePath() != null) {
			File dtdDirectory = new File(getResourcePath().toFileString() + "/" + PATH_DTD);
			if ((dtdDirectory.exists()) && (dtdDirectory.listFiles(this) != null)) {
				for (File DTDFile : dtdDirectory.listFiles(this)) {
					copyFile(URI.createFileURI(DTDFile.getAbsolutePath()), this.getResourceURI().toFileString());
				}
			} else {
				logger.warn("Cannot copy dtds fom resource directory, because resource directory '" + dtdDirectory.getAbsolutePath() + "' does not exist.");
			}
		} else {
			logger.warn("There is no reference to a resource path!");
		}

		try {
			mapTextualDataSources();
			mapTokens();
			mapSAudioDataSource();
			mapSpans();
			mapStructures();
			mapPointingRelations();
			mapSMetaAnnotations(getSDocument());
		} finally {
			for (PAULAPrinter printer : paulaPrinters.values()) {
				printer.close();
			}
		}

		return (DOCUMENT_STATUS.COMPLETED);
	}

	/**
	 * A factory to create {@link XMLStreamWriter} objects.
	 */
	private XMLOutputFactory xmlFactory = XMLOutputFactory.newFactory();

	/** A set storing all already used paula files. **/
	private Map<File, PAULAPrinter> paulaPrinters = new HashMap<File, Salt2PAULAMapper.PAULAPrinter>();

	/**
	 * Returns a {@link PAULAPrinter} corresponding to the given file. If no
	 * paulaPrinter exists so far, one is created
	 **/
	private PAULAPrinter getPAULAPrinter(File paulaFile) {
		PAULAPrinter retVal = paulaPrinters.get(paulaFile);
		if (retVal == null) {
			retVal = new PAULAPrinter(paulaFile);
			paulaPrinters.put(paulaFile, retVal);
		}
		return (retVal);
	}

	/** A helper class to create a {@link XMLStreamWriter} object. **/
	class PAULAPrinter {
		XMLStreamWriter xml = null;
		private PrintWriter output = null;
		private File paulaFile = null;
		private File base = null;

		public PAULAPrinter(File paulaFile) {
			this.paulaFile = paulaFile;
			try {
				output = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(paulaFile), "UTF8")), false);
				xml = new XMLStreamWriter(xmlFactory.createXMLStreamWriter(output));
				xml.setPrettyPrint(((PAULAExporterProperties) getProperties()).isHumanReadable());
				xml.writeStartDocument();
			} catch (IOException e) {
				throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot open file '" + paulaFile.getAbsolutePath() + "' to write to, because of a nested exception. ", e);
			} catch (XMLStreamException e) {
				throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + paulaFile.getAbsolutePath() + "', because of a nested exception. ", e);
			}
		}

		/**
		 * Closes the internal streams of this object. Make sure, to call this
		 * method for all {@link PAULAPrinter} objects even in case of
		 * exception.
		 **/
		public void close() {
			if (hasPreamble) {
				// close preamble
				try {
					xml.writeEndDocument();
					xml.flush();
				} catch (XMLStreamException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + paulaFile.getAbsolutePath() + "', because of a nested exception. ", e);
				}
			}
			output.flush();
			output.close();
		}

		/** returns whether preamble has been written **/
		public boolean hasPreamble() {
			return (hasPreamble);
		}

		boolean hasPreamble = false;

		/**
		 * Prints the preamble to multiple types of paula files.
		 * 
		 * @param paulaType
		 * @param paulaID
		 * @param type
		 * @param base
		 * @param xmlwriter
		 * @throws XMLStreamException
		 */
		public void printPreambel(PAULA_TYPE paulaType, String type, File base) {
			if (!hasPreamble) {
				if (paulaType == null)
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot create '" + paulaType + "' file beginning: This seems to be an internal problem.");
				if (type.isEmpty()) {
					type = paulaType.getFileInfix();
				}
				this.base = base;
				try {
					xml.writeDTD(paulaType.getDocTypeTag());
					xml.writeStartElement(TAG_PAULA);
					xml.writeAttribute(ATT_VERSION, VERSION);
					xml.writeEmptyElement(TAG_HEADER);
					xml.writeAttribute(ATT_PAULA_ID, paulaFile.getName().replace("." + PepperModule.ENDING_XML, ""));
					xml.writeStartElement(paulaType.getListElementName());
					xml.writeNamespace("xlink", XLINK_URI);
					xml.writeAttribute(ATT_TYPE, type);
					if (base != null) {
						xml.writeAttribute(ATT_BASE, base.getName());
					}
				} catch (XMLStreamException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + paulaFile.getAbsolutePath() + "', because of a nested exception. ", e);
				}
				hasPreamble = true;
			}
		}
	}

	private void mapSMetaAnnotations(SMetaAnnotatableElement container) {
		if (container != null) {
			if ((container.getSMetaAnnotations() != null) && (container.getSMetaAnnotations().size() > 0)) {

				// create anno-xml file
				String pathName = getResourceURI().toFileString();
				if (!pathName.endsWith("/")) {
					pathName = pathName + "/";
				}
				File annoFile = new File(pathName + "anno.xml");
				annoFile.getParentFile().mkdirs();
				PAULAPrinter printer = getPAULAPrinter(annoFile);
				try {
					printer.xml.writeDTD(PAULA_TEXT_DOCTYPE_TAG);
					printer.xml.writeStartElement(TAG_PAULA);
					printer.xml.writeAttribute(ATT_VERSION, VERSION);
					printer.xml.writeStartElement(TAG_HEADER);
					printer.xml.writeAttribute(ATT_PAULA_ID, "anno.xml");
					printer.xml.writeAttribute(ATT_TYPE, PAULA_TYPE.STRUCT.toString());
					printer.xml.writeEndElement();
					printer.xml.writeStartElement(TAG_STRUCT_STRUCTLIST);
					printer.xml.writeNamespace("xlink", XLINK_URI);
					printer.xml.writeAttribute(ATT_TYPE, "annoSet");
					printer.xml.writeStartElement(TAG_STRUCT_STRUCT);
					printer.xml.writeAttribute(ATT_ID, "anno_1");
					printer.xml.writeEndElement();
					printer.xml.writeEndElement();
					printer.xml.writeEndElement();
					printer.xml.writeEndDocument();
				} catch (XMLStreamException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + annoFile.getAbsolutePath() + "', because of a nested exception. ", e);
				} finally {
					printer.close();
				}

				for (SMetaAnnotation meta : container.getSMetaAnnotations()) {
					// create a file for each meta annotation
					String type = meta.getQName().replace("::", ".");
					String paulaID = "anno_" + type;
					String annoFileName = paulaID + "." + PepperImporter.ENDING_XML;

					File metaAnnoFile = new File(pathName + annoFileName);
					metaAnnoFile.getParentFile().mkdirs();
					printer = getPAULAPrinter(metaAnnoFile);
					if (!printer.hasPreamble) {
						printer.printPreambel(PAULA_TYPE.FEAT, type, annoFile);
					}
					try {
						String annoString = meta.getSValueSTEXT();
						if (annoString != null) {
							printer.xml.writeEmptyElement(TAG_FEAT_FEAT);
							printer.xml.writeAttribute(ATT_HREF, "#anno_1");
							printer.xml.writeAttribute(ATT_FEAT_FEAT_VAL, annoString);
						}
					} catch (XMLStreamException e) {
						throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + metaAnnoFile.getAbsolutePath() + "', because of a nested exception. ", e);
					}
				}
			}
		}

	}

	/**
	 * Maps all {@link STextualDS} objects.
	 */
	public void mapTextualDataSources() {
		// Iterate over all Textual Data Sources
		for (STextualDS sTextualDS : getSDocument().getSDocumentGraph().getSTextualDSs()) {
			File paulaFile = generateFileName(sTextualDS);
			PAULAPrinter printer = getPAULAPrinter(paulaFile);
			// Write the Text file content
			try {
				printer.xml.writeDTD(PAULA_TEXT_DOCTYPE_TAG);
				printer.xml.writeStartElement(TAG_PAULA);
				printer.xml.writeAttribute(ATT_VERSION, VERSION);
				printer.xml.writeStartElement(TAG_HEADER);
				printer.xml.writeAttribute(ATT_PAULA_ID, paulaFile.getName().replace("." + PepperImporter.ENDING_XML, ""));
				printer.xml.writeAttribute(ATT_TYPE, PAULA_TYPE.TEXT.toString());
				printer.xml.writeEndElement();
				printer.xml.writeStartElement(TAG_TEXT_BODY);
				printer.xml.writeCharacters(sTextualDS.getSText());
				printer.xml.writeEndElement();
				printer.xml.writeEndElement();
			} catch (XMLStreamException e) {
				throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + paulaFile.getAbsolutePath() + "', because of a nested exception. ", e);
			} finally {
				printer.close();
			}
		}
	}

	/**
	 * Maps all {@link SToken} objects.
	 */
	public void mapTokens() {
		PAULAPrinter printer = null;
		for (STextualRelation sTextRel : getSDocument().getSDocumentGraph().getSTextualRelations()) {
			SToken sToken = sTextRel.getSToken();
			if (sToken != null) {
				File paulaFile = generateFileName(sToken, sTextRel.getSTextualDS());
				printer = getPAULAPrinter(paulaFile);
				if (!printer.hasPreamble) {
					printer.printPreambel(PAULA_TYPE.TOK, "tok", generateFileName(sTextRel.getSTextualDS()));
				}
				try {
					if (((PAULAExporterProperties) getProperties()).isHumanReadable()) {
						printer.xml.writeComment(getSDocument().getSDocumentGraph().getSText(sToken));
					}
					printer.xml.writeEmptyElement(TAG_MARK_MARK);
					printer.xml.writeAttribute(ATT_ID, checkId(sToken.getSElementPath().fragment()));
					Integer start = sTextRel.getSStart() + 1;
					Integer end = sTextRel.getSEnd() - sTextRel.getSStart();
					String xPointer = "#xpointer(string-range(//body,''," + start + "," + end + "))";
					printer.xml.writeAttribute(ATT_HREF, xPointer);
				} catch (XMLStreamException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + paulaFile.getAbsolutePath() + "', because of a nested exception. ", e);
				}
			}
			mapAnnotations(sToken);
		}
		if (printer != null) {
			printer.close();
		}
	}

	/**
	 * Maps audio data as data source. When audio data are connected to tokens, 
	 * a span for each connection is created and annotated with an audio annotation referencing the 
	 * audio file. When no Token is connected to the audio source, a span is created for all tokens
	 * and an audio annotation is added to that span. 
	 */
	public void mapSAudioDataSource() {
		Multimap<SAudioDataSource, SToken> map = HashMultimap.create();
		if ((getSDocument().getSDocumentGraph().getSAudioDSRelations() != null) && (getSDocument().getSDocumentGraph().getSAudioDSRelations().size() > 0)) {
			/**
			 * Create a markable file which addresses all tokens, which have
			 * references to the SAudioDS
			 */
			for (SAudioDSRelation rel : getSDocument().getSDocumentGraph().getSAudioDSRelations()) {
				map.put(rel.getSAudioDS(), rel.getSToken());
			}
		} else {
			if ((getSDocument().getSDocumentGraph().getSAudioDataSources() != null) && (getSDocument().getSDocumentGraph().getSAudioDataSources().size() > 0))
				for (SAudioDataSource audioDS : getSDocument().getSDocumentGraph().getSAudioDataSources()) {
					map.putAll(audioDS, getSDocument().getSDocumentGraph().getSTokens());
				}
		}
		if (map.size() > 0) {
			StringBuffer fileName = new StringBuffer();
			fileName.append(getResourceURI().toFileString());
			if (!fileName.toString().endsWith("/")) {
				fileName.append("/");
			}
			fileName.append(getSDocument().getSName());
			fileName.append(".");
			fileName.append(PAULA_TYPE.MARK.getFileInfix());
			fileName.append(".");
			fileName.append("audio");
			fileName.append(".");
			fileName.append(PepperModule.ENDING_XML);
			File audioMarkFile = new File(fileName.toString());

			PAULAPrinter printer = getPAULAPrinter(audioMarkFile);

			if (!printer.hasPreamble) {
				printer.printPreambel(PAULA_TYPE.MARK, "audio", generateFileName(getSDocument().getSDocumentGraph().getSTokens().get(0)));
			}

			for (SAudioDataSource audio : map.keySet()) {
				try {
					printer.xml.writeEmptyElement(TAG_MARK_MARK);
					if (audio.getSElementPath().fragment() != null) {
						printer.xml.writeAttribute(ATT_ID, checkId(audio.getSElementPath().fragment()));
					} else {
						printer.xml.writeAttribute(ATT_ID, audio.getSId());
					}
					printer.xml.writeAttribute(ATT_HREF, generateXPointer(new ArrayList<SToken>(map.get(audio)), printer.base));
				} catch (XMLStreamException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + audioMarkFile.getAbsolutePath() + "', because of a nested exception. ", e);
				}
			}

			/**
			 * Create a feature file which addresses all tokens, which addresses
			 * the audio markable file
			 */
			// copy referenced files
			File audioFeatFile = new File(audioMarkFile.getAbsolutePath().replace("." + PepperModule.ENDING_XML, "_feat." + PepperModule.ENDING_XML));
			printer = getPAULAPrinter(audioFeatFile);
			printer.printPreambel(PAULA_TYPE.FEAT, "audio", audioMarkFile);

			for (SAudioDataSource audio : getSDocument().getSDocumentGraph().getSAudioDataSources()) {
				/**
				 * Copy audio file and
				 */
				String target = audioMarkFile.getAbsoluteFile().getParent();
				if (!target.endsWith("/")) {
					target = target + "/";
				}
				target = target + audio.getSAudioReference().lastSegment();
				File audioFile = new File(target);
				try {
					String source = audio.getSAudioReference().toFileString();
					if (source == null) {
						source = audio.getSAudioReference().toString();
					}
					Files.copy(new File(source).toPath(), audioFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot copy audio file '" + audio.getSAudioReference() + "', to +'" + target + "'. ", e);
				}
				/**
				 * Create a feature file which addresses all tokens, which
				 * addresses the audio marable file
				 */
				try {
					printer.xml.writeEmptyElement(TAG_FEAT_FEAT);
					printer.xml.writeAttribute(ATT_HREF, "#" + audio.getSElementPath().fragment());
					printer.xml.writeAttribute(ATT_FEAT_FEAT_VAL, audioFile.getName());
				} catch (XMLStreamException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + audioFeatFile.getAbsolutePath() + "', because of a nested exception. ", e);
				}

			}
		}
	}
	
	/**
	 * Maps all {@link SSpan} objects.
	 */
	public void mapSpans() {
		PAULAPrinter printer = null;
		for (SSpan sSpan : getSDocument().getSDocumentGraph().getSSpans()) {
			EList<STYPE_NAME> rels = new BasicEList<STYPE_NAME>();
			rels.add(STYPE_NAME.STEXT_OVERLAPPING_RELATION);
			List<SToken> tokens = getSDocument().getSDocumentGraph().getSortedSTokenByText(getSDocument().getSDocumentGraph().getOverlappedSTokens(sSpan, rels));
			if (tokens.size() > 0) {
				File paulaFile = generateFileName(sSpan);
				printer = getPAULAPrinter(paulaFile);

				if (!printer.hasPreamble) {
					printer.printPreambel(PAULA_TYPE.MARK, generatePaulaType(sSpan), generateFileName(tokens.get(0)));
				}
				try {
					if (((PAULAExporterProperties) getProperties()).isHumanReadable()) {
						printer.xml.writeComment(getSDocument().getSDocumentGraph().getSText(sSpan));
					}
					printer.xml.writeEmptyElement(TAG_MARK_MARK);
					if (sSpan.getSElementPath().fragment() != null) {
						printer.xml.writeAttribute(ATT_ID, checkId(sSpan.getSElementPath().fragment()));
					} else {
						printer.xml.writeAttribute(ATT_ID, sSpan.getSId());
					}
					printer.xml.writeAttribute(ATT_HREF, generateXPointer(tokens, printer.base));
				} catch (XMLStreamException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + paulaFile.getAbsolutePath() + "', because of a nested exception. ", e);
				}
			}
			mapAnnotations(sSpan);

		}
		if (printer != null) {
			printer.close();
		}
	}

	/**
	 * Maps {@link SStructure}
	 */
	private void mapStructures() {
		for (SStructure struct : getSDocument().getSDocumentGraph().getSStructures()) {
			File paulaFile = generateFileName(struct);
			PAULAPrinter printer = getPAULAPrinter(paulaFile);
			if (!printer.hasPreamble) {
				printer.printPreambel(PAULA_TYPE.STRUCT, generatePaulaType(struct), null);
			}
			try {
				if (((PAULAExporterProperties) getProperties()).isHumanReadable()) {
					printer.xml.writeComment(getSDocument().getSDocumentGraph().getSText(struct));
				}
				printer.xml.writeStartElement(TAG_STRUCT_STRUCT);
				printer.xml.writeAttribute(ATT_ID, checkId(struct.getSElementPath().fragment()));
				for (Edge edge : getSDocument().getSDocumentGraph().getOutEdges(struct.getSId())) {
					if (edge instanceof SDominanceRelation) {
						SDominanceRelation domRel = (SDominanceRelation) edge;
						printer.xml.writeEmptyElement(TAG_STRUCT_REL);
						String idVal = checkId(domRel.getSElementPath().fragment());
						if (idVal != null) {
							printer.xml.writeAttribute(ATT_ID, idVal);
						}
						if ((domRel.getSTypes() != null) && (!domRel.getSTypes().isEmpty())) {
							printer.xml.writeAttribute(ATT_STRUCT_REL_TYPE, domRel.getSTypes().get(0));
						}
						printer.xml.writeAttribute(ATT_HREF, generateXPointer(domRel.getSTarget(), printer.base));
						mapAnnotations(domRel);
					}
				}
				printer.xml.writeEndElement();
			} catch (XMLStreamException e) {
				throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + paulaFile.getAbsolutePath() + "', because of a nested exception. ", e);
			}
			mapAnnotations(struct);
		}
	}

	/**
	 * Maps {@link SPointingRelation}s.
	 * 
	 * @throws XMLStreamException
	 */
	private void mapPointingRelations() {
		for (SPointingRelation pointRel : getSDocument().getSDocumentGraph().getSPointingRelations()) {
			String type = "";
			if (pointRel.getSTypes() == null || pointRel.getSTypes().size() == 0) {
				type = "notype";
			} else {
				type = pointRel.getSTypes().get(0);
			}

			File paulaFile = generateFileName(pointRel);
			PAULAPrinter printer = getPAULAPrinter(paulaFile);
			if (!printer.hasPreamble) {
				printer.printPreambel(PAULA_TYPE.REL, type, null);
			}

			// create rel tag string
			if ((pointRel.getSSource() != null) && (pointRel.getSTarget() != null)) {
				try {
					printer.xml.writeEmptyElement(TAG_REL_REL);
					String idVal = checkId(pointRel.getSElementPath().fragment());
					if (idVal != null) {
						printer.xml.writeAttribute(ATT_ID, idVal);
					}
					printer.xml.writeAttribute(ATT_HREF, generateXPointer(pointRel.getSSource(), printer.base));
					printer.xml.writeAttribute(ATT_REL_REL_TARGET, generateXPointer(pointRel.getSTarget(), printer.base));
				} catch (XMLStreamException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + paulaFile.getAbsolutePath() + "', because of a nested exception. ", e);
				}
				mapAnnotations(pointRel);
			}
		}
	}

	/**
	 * Maps annotations of type {@link SAnnotation} and {@link SMetaAnnotation}
	 * 
	 * @param annoSource
	 */
	private void mapAnnotations(SAnnotatableElement annoSource) {
		if (annoSource != null) {
			for (SAnnotation anno : annoSource.getSAnnotations()) {
				String annoString = null;
				// copy referenced files
				if (anno.getSValueSURI() != null) {
					annoString = copyFile(anno.getSValueSURI(), getResourceURI().toFileString());
				} else {
					annoString = anno.getSValueSTEXT();
				}
				File paulaFile = generateFileName(anno);
				PAULAPrinter printer = getPAULAPrinter(paulaFile);
				if (!printer.hasPreamble) {
					String type = anno.getQName().replace("::", ".");
					if (annoSource instanceof SNode) {
						printer.printPreambel(PAULA_TYPE.FEAT, type, generateFileName((SNode) annoSource));
					} else if (annoSource instanceof SRelation) {
						printer.printPreambel(PAULA_TYPE.FEAT, type, generateFileName((SRelation) annoSource));
					}
				}
				try {
					if (annoString != null) {
						printer.xml.writeEmptyElement(TAG_FEAT_FEAT);
						printer.xml.writeAttribute(ATT_HREF, generateXPointer((SIdentifiableElement) annoSource, printer.base));
						printer.xml.writeAttribute(ATT_FEAT_FEAT_VAL, annoString);
					}
				} catch (XMLStreamException e) {
					throw new PepperModuleException(Salt2PAULAMapper.this, "Cannot write in file '" + paulaFile.getAbsolutePath() + "', because of a nested exception. ", e);
				}
			}
		}
	}

	/** a prefix for ids, which starts with a numeric **/
	public static final String ID_PREFIX = "id";

	/**
	 * Checks whether an id starts with a numeric, if true, the id will be
	 * prefixed with {@link #ID_PREFIX}
	 **/
	public String checkId(String id) {
		if ((id != null) && (Character.isDigit(id.charAt(0)))) {
			return (ID_PREFIX + id);
		}
		return (id);
	}

	/**
	 * Generates an xpointer for a set of {@link SNode}s.
	 * 
	 * @param targets
	 * @return
	 */
	public String generateXPointer(SIdentifiableElement target, File base) {
		StringBuilder retVal = new StringBuilder();
		if (target != null) {
			// write single node #tok_1
			File baseFile = null;
			if (target instanceof SNode) {
				baseFile = generateFileName((SNode) target);
			} else if (target instanceof SRelation) {
				baseFile = generateFileName((SRelation) target);
			}
			if (!baseFile.equals(base)) {
				retVal.append(baseFile.getName());
			}
			retVal.append("#");
			String fragment = target.getSElementPath().fragment();
			if (fragment == null) {
				// fix to fix a bug in mmaxmodules, where id was created
				// manually
				fragment = target.getSId();
			}
			retVal.append(checkId(fragment));
		}
		return (retVal.toString());
	}

	/**
	 * Generates an xpointer for a set of {@link SIdentifiableElement}s.
	 * 
	 * @param targets
	 * @return
	 */
	public String generateXPointer(List<? extends SIdentifiableElement> targets, File base) {
		StringBuilder retVal = new StringBuilder();
		if ((targets != null) && (targets.size() > 0)) {
			if (targets.size() == 1) {
				retVal.append(generateXPointer(targets.get(0), base));
			} else {
				// write all nodes e.g.: (#tok_1 #tok2 ... #tok_n)
				int i = 0;
				for (SIdentifiableElement target : targets) {
					if (i != 0) {
						retVal.append(" ");
					}
					retVal.append(generateXPointer(target, base));
					i++;
				}
			}
		}
		return (retVal.toString());
	}

	public static final String NO_LAYER = "no_layer";

	/**
	 * Generates a Paula type from the layers of passed {@link SNode} object.
	 * 
	 * @param sNode
	 * @return
	 */
	public String generatePaulaType(SIdentifiableElement sElement) {
		String layers = NO_LAYER;
		if (sElement != null) {
			List<SLayer> sLayers = null;
			if (sElement instanceof SNode) {
				sLayers = ((SNode) sElement).getSLayers();
			} else if (sElement instanceof SRelation) {
				sLayers = ((SRelation) sElement).getSLayers();
			}
			if (sLayers.size() > 0) {
				// if node belongs to several layers, sort layers by name
				if (sLayers.size() == 1) {
					layers = sLayers.get(0).getSName();
				} else {
					List<String> layerList = new ArrayList<String>();
					for (SLayer sLayer : sLayers) {
						layerList.add(sLayer.getSName());
					}
					Collections.sort(layerList, String.CASE_INSENSITIVE_ORDER);
					int i = 0;
					for (String layerName : layerList) {
						if (i == 0) {
							layers = layerName;
						} else {
							layers = layers + "." + layerName;
						}
					}
				}
			}
		}
		return (layers);
	}

	/**
	 * Returns a filename, where to store the given SNode. The pattern, which is
	 * used to compute the files name is: <br/>
	 * layers"."documentId"."TYPE_POSTFIX".xml" <br/>
	 * If node belongs to several layers, they are sorted by name.
	 * 
	 * @param sNode
	 *            {@link SNode} to which a filename has to be generated
	 * @param sText
	 *            {@link STextualDS} node, which is referred by this node (only
	 *            in case of node is of type {@link SToken})
	 * @return file name matching to given {@link SNode}
	 */
	public File generateFileName(SNode sNode, STextualDS sText) {
		File retFile = null;
		if (sNode != null) {
			StringBuilder fileName = new StringBuilder();

			if (sNode instanceof STextualDS) {
				fileName.append(getSDocument().getSName());
				fileName.append(".");
				fileName.append(PAULA_TYPE.TEXT.getFileInfix());
				if (getSDocument().getSDocumentGraph().getSTextualDSs().size() > 1) {
					fileName.append((getSDocument().getSDocumentGraph().getSTextualDSs().indexOf(sNode) + 1));
				}
			} else if (sNode instanceof SToken) {
				fileName.append(getSDocument().getSName());
				fileName.append(".");
				if ((sText != null) && (getSDocument().getSDocumentGraph().getSTextualDSs().size() > 1)) {
					fileName.append(getSDocument().getSDocumentGraph().getSTextualDSs().indexOf(sText));
				}
				fileName.append(PAULA_TYPE.TOK.getFileInfix());
			} else {
				// prefix file name with layer names
				String layers = generatePaulaType(sNode);
				fileName.append(layers);
				if (!layers.isEmpty()) {
					fileName.append(".");
				}
				fileName.append(getSDocument().getSName());
				fileName.append(".");
				if (sNode instanceof SSpan) {
					fileName.append(PAULA_TYPE.MARK.getFileInfix());
				} else if (sNode instanceof SStructure) {
					fileName.append(PAULA_TYPE.STRUCT.getFileInfix());
				}
			}
			fileName.append(".");
			fileName.append(PepperModule.ENDING_XML);
			String pathName = getResourceURI().toFileString();
			if (!pathName.endsWith("/")) {
				pathName = pathName + "/";
			}
			retFile = new File(pathName + fileName.toString());
			retFile.getParentFile().mkdirs();
		}

		return (retFile);
	}

	/**
	 * Returns a filename, where to store the given SNode. The pattern, which is
	 * used to compute the files name is: <br/>
	 * layers"."documentId"."TYPE_POSTFIX".xml" <br/>
	 * If node belongs to several layers, they are sorted by name.
	 * 
	 * @param sNode
	 *            {@link SNode} to which a filename has to be generated
	 * @return file name matching to given {@link SNode}
	 */
	public File generateFileName(SNode sNode) {
		return (generateFileName(sNode, null));
	}

	/**
	 * Returns a filename, where to store the given {@link SRelation}.
	 * 
	 * @param sNode
	 *            {@link SNode} to which a filename has to be generated
	 * @return file name matching to given {@link SNode}
	 */
	public File generateFileName(SRelation sRelation) {
		if (sRelation instanceof SDominanceRelation) {
			return (generateFileName(sRelation.getSSource()));
		} else if (sRelation instanceof SPointingRelation) {
			StringBuilder fileName = new StringBuilder();
			String layers = generatePaulaType(sRelation);
			fileName.append(layers);
			if (!layers.isEmpty()) {
				fileName.append(".");
			}
			fileName.append(getSDocument().getSName());
			fileName.append(".");
			String type = "";
			if (sRelation.getSTypes() == null || sRelation.getSTypes().size() == 0) {
				type = "notype";
			} else {
				type = sRelation.getSTypes().get(0);
			}
			fileName.append(type);
			fileName.append(".");
			fileName.append(PepperModule.ENDING_XML);
			String pathName = getResourceURI().toFileString();
			if (!pathName.endsWith("/")) {
				pathName = pathName + "/";
			}
			File retFile = new File(pathName + fileName.toString());
			retFile.getParentFile().mkdirs();
			return (retFile);

		} else
			return (null);
	}

	/**
	 * Returns a filename, where to store the given SAnnotation.
	 * 
	 * @param sNode
	 *            {@link SNode} to which a filename has to be generated
	 * @return file name matching to given {@link SNode}
	 */
	public File generateFileName(Label anno) {

		File baseFileName = null;
		if (anno.getLabelableElement() instanceof SNode) {
			baseFileName = generateFileName((SNode) anno.getLabelableElement());
		} else if (anno.getLabelableElement() instanceof SRelation) {
			baseFileName = generateFileName((SRelation) anno.getLabelableElement());
		}

		StringBuilder fileName = new StringBuilder();
		fileName.append(baseFileName.getName().replace(".xml", ""));
		fileName.append("_");
		fileName.append(anno.getName());
		fileName.append(".xml");
		return (new File(baseFileName.getParent() + "/" + fileName.toString()));
	}

	/**
	 * Method for copying file to outputPath. This is used for copying DTDs and
	 * media files (e.g. mp3-files)
	 * 
	 * @param file
	 *            the file (URI) to copy
	 * @param outputPath
	 *            the target path to copy to
	 * @return the filename in the form &quot; file:/filename &quot;
	 */
	private String copyFile(URI file, String outputPath) {
		File inFile = new File(file.toFileString());
		File outFile = new File(outputPath + "/" + inFile.getName());

		FileInputStream in = null;
		FileOutputStream out = null;
		String outFileString = null;
		try {
			in = new FileInputStream(file.toFileString());
			out = new FileOutputStream(outFile);
			int c;

			while ((c = in.read()) != -1) {
				out.write(c);
			}
			outFileString = "file:/" + outFile.getName();
		} catch (IOException e) {
			throw new PepperModuleException(this, "Cannot copy file '" + file + "' to path '" + outFileString + "'", e);
		}
		return outFileString;
	}
}
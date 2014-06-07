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

import java.util.Collection;

import org.eclipse.emf.common.util.URI;
import org.osgi.service.component.annotations.Component;

import de.hu_berlin.german.korpling.saltnpepper.pepper.modules.PepperImporter;
import de.hu_berlin.german.korpling.saltnpepper.pepper.modules.PepperMapper;
import de.hu_berlin.german.korpling.saltnpepper.pepper.modules.impl.PepperImporterImpl;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SCorpus;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SElementId;

/**
 * This importer reads a corpus in PAULA format and maps it to a SALT corpus. 
 * The mapping of each document is done in a separate thread. 
 * 
 * @author Florian Zipser
 * @version 1.0
 *
 */
@Component(name="PAULAImporterComponent", factory="PepperImporterComponentFactory")
public class PAULAImporter extends PepperImporterImpl implements PepperImporter
{
	public PAULAImporter()
	{
		super();
		
		//setting name of module
		setName("PAULAImporter");
		
		//set list of formats supported by this module
		this.addSupportedFormat("paula", "1.0", null);
		
		this.getSDocumentEndings().add(ENDING_LEAF_FOLDER);
	}
		
	/**
	 * Stores the endings which are used for paula-files
	 */
	private String[] PAULA_FILE_ENDINGS= {"xml","paula"};	
	/**
	 * Creates a mapper of type {@link PAULA2SaltMapper}.
	 * {@inheritDoc PepperModule#createPepperMapper(SElementId)}
	 */
	@Override
	public PepperMapper createPepperMapper(SElementId sElementId)
	{
		PAULA2SaltMapper mapper= new PAULA2SaltMapper();
		mapper.setPAULA_FILE_ENDINGS(PAULA_FILE_ENDINGS);
		
		if (sElementId.getSIdentifiableElement() instanceof SCorpus)
		{//avoid importing of SCorpus, in case of SCorpus was artificially created and links to the same path as a SDocument object
			Collection<URI> pathes= this.getSElementId2ResourceTable().values();
			int i= 0;
			for (URI path: pathes)
			{
				if (path.equals(this.getSElementId2ResourceTable().get(sElementId)))
					i++;
			}
			if (i>1)
				mapper.setIsArtificialSCorpus(true);
		}//avoid importing of SCorpus, in case of SCorpus was artificially created and links to the same path as a SDocument object
		return(mapper);
	}
}

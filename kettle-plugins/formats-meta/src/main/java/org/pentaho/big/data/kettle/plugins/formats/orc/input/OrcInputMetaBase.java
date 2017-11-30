/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2017 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.big.data.kettle.plugins.formats.orc.input;

import org.apache.commons.vfs2.FileObject;
import org.pentaho.big.data.kettle.plugins.formats.FormatInputFile;
import org.pentaho.big.data.kettle.plugins.formats.FormatInputOutputField;
import org.pentaho.big.data.kettle.plugins.formats.orc.output.OrcOutputMetaBase;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleFileException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.injection.Injection;
import org.pentaho.di.core.row.value.ValueMetaBase;
import org.pentaho.di.core.vfs.AliasedFileObject;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.steps.file.BaseFileInputAdditionalField;
import org.pentaho.di.trans.steps.file.BaseFileInputMeta;
import org.pentaho.di.workarounds.ResolvableResource;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Orc input meta step without Hadoop-dependent classes. Required for read meta in the spark native code.
 *
 * @author Jacob Gminder
 */
public abstract class OrcInputMetaBase extends
    BaseFileInputMeta<BaseFileInputAdditionalField, FormatInputFile, FormatInputOutputField> implements ResolvableResource {

  private static final Class<?> PKG = OrcOutputMetaBase.class;

  @Injection( name = "ORC_FILENAME" )
  private String filename;

  protected List<FormatInputOutputField> inputFields = new ArrayList<FormatInputOutputField>();

  public OrcInputMetaBase() {
    additionalOutputFields = new BaseFileInputAdditionalField();
    inputFiles = new FormatInputFile();
  }


  public void allocateFiles( int nrFiles ) {
    inputFiles.environment = new String[nrFiles];
    inputFiles.fileName = new String[nrFiles];
    inputFiles.fileMask = new String[nrFiles];
    inputFiles.excludeFileMask = new String[nrFiles];
    inputFiles.fileRequired = new String[nrFiles];
    inputFiles.includeSubFolders = new String[nrFiles];
  }

  /**
   * TODO: remove from base
   */
  @Override
  public String getEncoding() {
    return null;
  }

  @Override
  public void setDefault() {
    allocateFiles( 0 );
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename( String filename ) {
    this.filename = filename;
  }

  public List<FormatInputOutputField> getInpuFields() {
    return inputFields;
  }

  public void setInputFields( List<FormatInputOutputField> inputFields ) {
    this.inputFields = inputFields;
  }

  @Override
  public void loadXML( Node stepnode, List<DatabaseMeta> databases, IMetaStore metaStore ) throws KettleXMLException {
    readData( stepnode, metaStore );
  }

  private void readData( Node stepnode, IMetaStore metastore ) throws KettleXMLException {
    try {
      String passFileds = XMLHandler.getTagValue( stepnode, "passing_through_fields" ) == null ? "false" : XMLHandler.getTagValue( stepnode, "passing_through_fields" );
      inputFiles.passingThruFields = ValueMetaBase.convertStringToBoolean( passFileds );
      filename = XMLHandler.getTagValue( stepnode, "filename" );
      Node fields = XMLHandler.getSubNode( stepnode, "fields" );
      int nrfields = XMLHandler.countNodes( fields, "field" );
      List<FormatInputOutputField> orcInputFields = new ArrayList<>();
      for ( int i = 0; i < nrfields; i++ ) {
        Node fnode = XMLHandler.getSubNodeByNr( fields, "field", i );
        FormatInputOutputField inputField = new FormatInputOutputField();
        inputField.setPath( XMLHandler.getTagValue( fnode, "path" ) );
        inputField.setName( XMLHandler.getTagValue( fnode, "name" ) );
        inputField.setType( XMLHandler.getTagValue( fnode, "type" ) );
        inputField.setNullString( XMLHandler.getTagValue( fnode, "nullable" ) );
        inputField.setIfNullValue( XMLHandler.getTagValue( fnode, "default" )  );
        inputField.setSourceType( XMLHandler.getTagValue( fnode, "sourcetype" ) );
        orcInputFields.add( inputField );
      }
      this.inputFields = orcInputFields;
    } catch ( Exception e ) {
      throw new KettleXMLException( "Unable to load step info from XML", e );
    }
  }

  @Override
  public String getXML() {
    StringBuffer retval = new StringBuffer( 800 );
    final String INDENT = "    ";

    retval.append( INDENT ).append( XMLHandler.addTagValue( "passing_through_fields", inputFiles.passingThruFields ) );
    retval.append( INDENT ).append( XMLHandler.addTagValue( "filename", getFilename() ) );

    retval.append( "    <fields>" ).append( Const.CR );
    for ( int i = 0; i < inputFields.size(); i++ ) {
      FormatInputOutputField field = inputFields.get( i );

      if ( field.getName() != null && field.getName().length() != 0 ) {
        retval.append( "      <field>" ).append( Const.CR );
        retval.append( "        " ).append( XMLHandler.addTagValue( "path", field.getPath() ) );
        retval.append( "        " ).append( XMLHandler.addTagValue( "name", field.getName() ) );
        retval.append( "        " ).append( XMLHandler.addTagValue( "type", field.getTypeDesc() ) );
        retval.append( "        " ).append( XMLHandler.addTagValue( "nullable", field.getNullString() ) );
        retval.append( "        " ).append( XMLHandler.addTagValue( "default", field.getIfNullValue() ) );
        retval.append( "        " ).append( XMLHandler.addTagValue( "sourcetype", field.getSourceTypeDesc() ) );
        retval.append( "      </field>" ).append( Const.CR );
      }
    }
    retval.append( "    </fields>" ).append( Const.CR );

    return retval.toString();
  }

  @Override
  public void readRep( Repository rep, IMetaStore metaStore, ObjectId id_step, List<DatabaseMeta> databases )
      throws KettleException {
    try {

      inputFiles.passingThruFields = rep.getStepAttributeBoolean( id_step, "passing_through_fields" );
      filename = rep.getStepAttributeString( id_step, "filename" );

      // using the "type" column to get the number of field rows because "type" is guaranteed not to be null.
      int nrfields = rep.countNrStepAttributes( id_step, "type" );

      List<FormatInputOutputField> orcOutputFields = new ArrayList<>();
      for ( int i = 0; i < nrfields; i++ ) {
        FormatInputOutputField inputField = new FormatInputOutputField();

        inputField.setPath( rep.getStepAttributeString( id_step, i, "path" ) );
        inputField.setName( rep.getStepAttributeString( id_step, i, "name" ) );
        inputField.setType( rep.getStepAttributeString( id_step, i, "type" ) );
        inputField.setIfNullValue( rep.getStepAttributeString( id_step, i, "nullable" ) );
        inputField.setNullString( rep.getStepAttributeString( id_step, i, "default" ) );
        inputField.setSourceType( rep.getStepAttributeString( id_step, i, "sourcetype" ) );

        orcOutputFields.add( inputField );
      }
      this.inputFields = orcOutputFields;
    } catch ( Exception e ) {
      throw new KettleException( "Unexpected error reading step information from the repository", e );
    }
  }

  @Override
  public void saveRep( Repository rep, IMetaStore metaStore, ObjectId id_transformation, ObjectId id_step )
      throws KettleException {
    try {
      rep.saveStepAttribute( id_transformation, id_step, "passing_through_fields", inputFiles.passingThruFields );
      rep.saveStepAttribute( id_transformation, id_step, "filename", getFilename() );
      for ( int i = 0; i < inputFields.size(); i++ ) {
        FormatInputOutputField field = inputFields.get( i );

        rep.saveStepAttribute( id_transformation, id_step, i, "path", field.getPath() );
        rep.saveStepAttribute( id_transformation, id_step, i, "name", field.getName() );
        rep.saveStepAttribute( id_transformation, id_step, i, "type", field.getTypeDesc() );
        rep.saveStepAttribute( id_transformation, id_step, i, "nullable", field.getIfNullValue() );
        rep.saveStepAttribute( id_transformation, id_step, i, "default", field.getNullString() );
        rep.saveStepAttribute( id_transformation, id_step, i, "sourcetype", field.getSourceTypeDesc() );
      }
      super.saveRep( rep, metaStore, id_transformation, id_step );
    } catch ( Exception e ) {
      throw new KettleException( "Unable to save step information to the repository for id_step=" + id_step, e );
    }
  }

  @Override
  public void resolve() {
    if ( filename != null && !filename.isEmpty() ) {
      try {
        String realFileName = getParentStepMeta().getParentTransMeta().environmentSubstitute( filename );
        FileObject fileObject = KettleVFS.getFileObject( realFileName );
        if ( AliasedFileObject.isAliasedFile( fileObject ) ) {
          filename = ( (AliasedFileObject) fileObject ).getOriginalURIString();
        }
      } catch ( KettleFileException e ) {
        throw new RuntimeException( e );
      }
    }
  }

}

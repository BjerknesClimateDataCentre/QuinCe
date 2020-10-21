
'''
Functions specific to communication with Copernicus.

Files to Copernicus must be on netcdf format.

Files are sent to Copernicus by FTP. 

The Copernicus FTP requires an Index file and a DNT file describing all 
files uploaded to the server to complete the ingestion. 
The index file reflects all the files in the FTP folder.
The DNT file triggers the ingestion. 

Example of DNT file format provided by mail from Antoine.Queric@ifremer.fr 2019-03-07
<?xml version="1.0" ?>
<delivery PushingEntity="CopernicusMarine-InSitu-Global" date="20190306T070107Z" product="INSITU_GLO_CARBON_NRT_OBSERVATIONS_013_049">
  <dataset DatasetName="NRT">
    <file Checksum="936999b6a47731e8aa763ec39b3af641" FileName="latest/20190306/A.nc" FinalStatus="Delivered" StartUploadTime="20190306T070107Z" StopUploadTime="20190306T070107Z"/>
    <file Checksum="d763859d86284add3395067fe9f8e3a0" FileName="latest/20190306/B.nc" FinalStatus="Delivered" StartUploadTime="20190306T070108Z" StopUploadTime="20190306T070108Z"/>

    <file FileName="latest/20190306/C.nc">
      <KeyWord>Delete</KeyWord>
    </file>

  </dataset>
</delivery> 

Example of index file format provided by Corentin.Guyot@ifremer.fr 2019-03-06
# Title : Carbon in-situ observations catalog 
# Description : catalog of available in-situ observations per platform. 
# Project : Copernicus 
# Format version : 1.0 
# Date of update : 20190305080103 
# catalog_id,file_name,geospatial_lat_min,geospatial_lat_max,geospatial_lon_min,geospatial_lon_max,time_coverage_start,time_coverage_end,provider,date_update,data_mode,parameters 
COP-GLOBAL-01,ftp://nrt.cmems-du.eu/Core/INSITU_GLO_CARBON_NRT_OBSERVATIONS_013_049/nrt/latest/20190221/GL_LATEST_PR_BA_7JXZ_20190221.nc,19.486,19.486,-176.568,-176.568,2019-02-21T17:50:00Z,2019-02-21T17:50:00Z,Unknown institution,2019-02-24T04:10:11Z,R,DEPH TEMP

To delete en empty directory, you can use the following syntax inside your DNT file :

        <directory DestinationFolderName="" SourceFolderName="directoryName">
          <KeyWord>Delete</KeyWord>
        </directory>


To move an existing file, you can use following syntax in your DNT file :

        <file Checksum="fileChecksum" FileName="path/to/existing/file.nc" NewFileName="path/to/new_folder/file.nc">
            <KeyWord>Move</KeyWord>
        </file>

'''
import logging 
import ftputil 
import os
import sys
import re
import hashlib
import datetime
import pandas as pd
import numpy as np
import netCDF4
from modules.CMEMS.cmems_converter import buildnetcdfs 
from modules.Local.data_processing import get_file_from_zip

import xml.etree.ElementTree as ET
import sqlite3
import json
import time
import toml

with open('config_copernicus.toml') as f: config_copernicus = toml.load(f)

# Response codes
UPLOADED = 1
NOT_UPLOADED = 0
FAILED_INGESTION = -1


log_file = 'log/cmems_log.txt'
not_ingested = 'log/log_uningested_files.csv'
cmems_db = 'files_cmems.db'

product_id = 'INSITU_GLO_CARBON_NRT_OBSERVATIONS_013_049'
dataset_id = 'NRT_202003'

nrt_dir = '/' + product_id + '/' + dataset_id + '/latest'
dnt_dir = '/' + product_id + '/DNT'
index_dir = '/' + product_id + '/' + dataset_id

local_folder = 'latest'

def build_dataproduct(dataset_zip,dataset,key,destination_filename,platform):
  '''
  transforms csv-file to daily netCDF-files.
  Creates dictionary containing info on each netCDF file extracted
  requires: zip-folder, dataset-name and specific filename of csv-file.
  returns: dictionary
  '''
  # BUILD netCDF FILES

  data_filename = (dataset['name'] + '/dataset/' + "Copernicus" + key 
  + dataset['name'] + '.csv')

  # Load field config
  fieldconfig = pd.read_csv('fields.csv', delimiter=',', quotechar='\'')

  csv_file = get_file_from_zip(dataset_zip, destination_filename)  
  
  curr_date = datetime.datetime.now().strftime("%Y%m%d")
  
  if not os.path.exists(local_folder): os.mkdir(local_folder)

  logging.info(f'Creating netcdf-files based on {csv_file} to send to CMEMS')

  filedata = pd.read_csv(csv_file, delimiter=',')
  nc_files = buildnetcdfs(dataset_name, fieldconfig, filedata,platform)
   
  nc_dict = {}
  for nc_file in nc_files:
    (nc_filename, nc_content) = nc_file
    hashsum = hashlib.md5(nc_content).hexdigest()
    logging.debug(f'Processing netCDF file {nc_filename}')

    # ASSIGN DATE-VARIABLES TO netCDF FILE
    nc_filepath = local_folder + '/' + nc_filename + '.nc'   

    with open(nc_filepath,'wb') as f: f.write(nc_content)

    # reading netCDF file to memory
    nc = netCDF4.Dataset(nc_filepath,mode = 'r+')
    datasetdate = datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")

    nc.date_update = datasetdate
    nc.history = datasetdate + " : Creation"

    platform_code = nc.platform_code
    last_lat = nc.last_latitude_observation
    last_lon = nc.last_longitude_observation
    last_dt = nc.last_date_observation 

    #get list of parameters from netCDF file
    var_list = nc.variables.keys()
    var_list = list(filter(lambda x: '_' not in x, var_list))
    var_list = list(filter(lambda x: 'TIME' not in x, var_list))
    var_list = list(filter(lambda x: 'LATITUDE' not in x, var_list))
    var_list = list(filter(lambda x: 'LONGITUDE' not in x, var_list))
    parameters = ' '.join(var_list)
    nc.close()

    # create dictionary object
    date = nc_filename.split('_')[-1]
    date = datetime.datetime.strptime(date,'%Y%m%d')
    hashsum = hashlib.md5(nc_content).hexdigest()
    nc_dict[nc_filename] = ({
      'filepath':nc_filepath, 
      'hashsum': hashsum, 
      'date': date, 
      'dataset':dataset_name,
      'uploaded':False,
      'platform': platform_code,
      'parameters':parameters,
      'last_lat':last_lat,
      'last_lon':last_lon,
      'last_dt':last_dt})

  logging.debug(f'Commiting metadata to local SQL database {cmems_db}')
  sql_commit(nc_dict)
  return str(curr_date)


def upload_to_copernicus(ftp_config,server,dataset,curr_date,platform):
  '''
  - Creates a FTP-connection
  - Uploads netCDF files
  - Creates and uploads index file and DNT file(s).
  - Checks response file generated by cmems to identify any failed uploads.

  ftp_config contains login information
  '''
  status = 0
  error = curr_date
  error_msg = ''


  # create ftp-connection
  with ftputil.FTPHost(
    host=ftp_config['Copernicus'][server],
    user=ftp_config['Copernicus']['user'],
    passwd=ftp_config['Copernicus']['password'])as ftp:

    c = create_connection(cmems_db)

  # CHECK IF FTP IS EMPTY 
    logging.debug('Checking FTP directory')
    directory_not_empty = check_directory(ftp, nrt_dir) 
    if directory_not_empty:
      logging.error('Previous export has failed, \
        clean up remanent files before re-exporting')
    else:
    # CHECK DICTONARY : DELETE FILES ON SERVER; OLDER THAN 30 DAYS
      logging.debug('Checking local database')
      c.execute("SELECT * FROM latest \
       WHERE (nc_date < date('now','-30 day') AND uploaded == ?)",[UPLOADED]) 
      results_delete = c.fetchall()
      logging.debug(f'delete {len(results_delete)}; {results_delete}')
      
      dnt_delete = {}
      for item in results_delete: 
        filename, filepath_local  = item[0], item[2]
        dnt_delete[filename] = item[6]
        c.execute("UPDATE latest SET uploaded = ? \
          WHERE filename = ?", [NOT_UPLOADED, filename])

    # CHECK DICTIONARY: UPLOAD FILES NOT ON SERVER; YOUNGER THAN 30 DAYS
      c.execute("SELECT * FROM latest \
        WHERE (nc_date >= date('now','-30 day') \
        AND NOT uploaded == ?)",[UPLOADED]) 
      results_upload = c.fetchall()    
      if len(results_upload) == 0:
        status = 2 
        logging.debug('All files already exported')
      else:
        logging.debug(f'Upload {len(results_upload)}: {results_upload}')

      dnt_upload = {}
      for item in results_upload:
        filename, filepath_local  = item[0], item[2]
        
        upload_result, filepath_ftp, start_upload_time, stop_upload_time = (
          upload_to_ftp(ftp, ftp_config, filepath_local))
        logging.debug(f'upload result: {upload_result}')
        
        if upload_result == 0: #upload ok
          # Setting dnt-variable to temp variable: curr_date.
          # After DNT is created, the DNT-filepath is updated for all  
          # instances where DNT-filetpath is curr_date
          c.execute("UPDATE latest \
            SET uploaded = ?, ftp_filepath = ?, dnt_file = ? \
            WHERE filename = ?", 
            [UPLOADED, filepath_ftp, curr_date ,filename])

          # create DNT-entry
          dnt_upload[filename] = ({'ftp_filepath':filepath_ftp, 
            'start_upload_time':start_upload_time, 
            'stop_upload_time':stop_upload_time,
            'local_filepath':local_folder+'/'+filename +'.nc'})    
          logging.debug(f'dnt entry: {dnt_upload[filename]}') 
        else:
          logging.debug(f'upload failed: {upload_result}')

      if dnt_upload or dnt_delete:
        # FETCH INDEX
        c.execute("SELECT * FROM latest WHERE uploaded == 1")
        currently_uploaded = c.fetchall()

        try:
          index_filename = build_index(currently_uploaded)
        except Exception as e:
          logging.error('Building index failed: ', exc_info=True)
          status = 0
          error += 'Building index failed: ' + str(e)
   
        # UPLOAD INDEX 
        if index_filename:
          try:
            upload_result, ftp_filepath, start_upload_time, stop_upload_time = (
              upload_to_ftp(ftp,ftp_config, index_filename))
            logging.debug(f'index upload result: {upload_result}')
          except Exception as e:
            logging.error('Uploading index failed: ', exc_info=True)
            status = 0      
            error += 'Uploading index failed: ' + str(e)
        
          # BUILD DNT-FILE
          # Adding index file to DNT-list:
          dnt_upload[index_filename] = ({
            'ftp_filepath':ftp_filepath, 
            'start_upload_time':start_upload_time, 
            'stop_upload_time':stop_upload_time,
            'local_filepath': index_filename,
            })

        
        # INDEX platform
        try:
          index_platform = build_index_platform(c,platform)
        except Exception as e:
          logging.error('Building platform index failed: ', exc_info=True)
          status = 0
          error += 'Building platform index failed: ' + str(e)
   
        if index_platform:
          try:
            upload_result, ftp_filepath, start_upload_time, stop_upload_time = (
              upload_to_ftp(ftp,ftp_config, index_platform))
            logging.debug(f'index platform upload result: {upload_result}')
          except Exception as e:
            logging.error('Uploading platform index failed: ', exc_info=True)
            status = 0      
            error += 'Uploading platform index failed: ' + str(e)
        
          # BUILD DNT-FILE
          # Adding index file to DNT-list:
          dnt_upload[index_platform] = ({
            'ftp_filepath':ftp_filepath, 
            'start_upload_time':start_upload_time, 
            'stop_upload_time':stop_upload_time,
            'local_filepath': index_platform,

            })

        logging.info('Building and uploading DNT-file')
        try:
          dnt_file, dnt_local_filepath = build_DNT(dnt_upload,dnt_delete)

          # UPLOAD DNT-FILE
          _, dnt_ftp_filepath, _, _ = (
            upload_to_ftp(ftp, ftp_config, dnt_local_filepath))
          
          logging.debug('Updating database to include DNT filename')
          sql_rec = "UPDATE latest SET dnt_file = ? WHERE dnt_file = ?"
          sql_var = [dnt_local_filepath, curr_date]
          c.execute(sql_rec,sql_var)

          try:
            response = evaluate_response_file(
              ftp,dnt_ftp_filepath,dnt_local_filepath.rsplit('/',1)[0],cmems_db)
            logging.debug('cmems dnt-response: {}'.format(response))
            if len(response) == 0: status = 1

          except Exception as e:
            logging.error('No response from CMEMS: ', exc_info=True)
            status = 0
            error += 'No response from CMEMS: ' + str(e)

        except Exception as exception:
          logging.error('Building DNT failed: ', exc_info=True)
          status = 0
          error += 'Building DNT failed: ' + str(exception)

        # FOLDER CLEAN UP
        if dnt_delete:
          logging.info('Delete empty directories')
          try: 
            _, dnt_local_filepath_f = build_fDNT(dnt_delete)

            _, dnt_ftp_filepath_f, _, _ = (
              upload_to_ftp(ftp, ftp_config, dnt_local_filepath_f))  
            try:
              response = evaluate_response_file(
                ftp,dnt_ftp_filepath_f,dnt_local_filepath_f.rsplit('/',1)[0],cmems_db)
              logging.debug('cmems fDNT-response, delete empty folders: {}'.format(response))

            except Exception as e:
              logging.error('No response from CMEMS: ', exc_info=True)
              error += 'No response from CMEMS: ' + str(e)

          except Exception as e:
            logging.error('Uploading fDNT failed: ', exc_info=True)
            error += 'Uploading fDNT failed: ' + str(e)

      if status == 0:
        logging.error('Upload failed')
        error_msg = abort_upload(error, ftp, nrt_dir, c, curr_date)
        
    return status, error_msg

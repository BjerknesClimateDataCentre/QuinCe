'''
CMEMS module
Contains functions related to FTP processes

Maren K. Karlsen 2020.10.29
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

import xml.etree.ElementTree as ET
import sqlite3
import json
import time

from modules.CMEMS.Export_CMEMS_sql import update_db_new_submission, abort_upload_db

# Http upload result codes
UPLOAD_OK = 0
FILE_EXISTS = 2

# Status codes
UPLOADED = 1
NOT_UPLOADED = -9
FAILED_INGESTION = -1

DNT_DATETIME_FORMAT = '%Y-%m-%dT%H:%M:%SZ'
server_location = 'ftp://nrt.cmems-du.eu/Core'

NOT_INGESTED = 'log/log_uningested_files.csv'

PRODUCT_ID = 'INSITU_GLO_CARBON_NRT_OBSERVATIONS_013_049'
DATASET_ID = 'NRT_202003'

NRT_DIR = '/' + PRODUCT_ID + '/' + DATASET_ID + '/latest'
DNT_DIR = '/' + PRODUCT_ID + '/DNT'
INDEX_DIR = '/' + PRODUCT_ID + '/' + DATASET_ID

LOCAL_FOLDER = 'latest'
LATEST_DAYS = 30

def delete_files_older_than_x_days(db):
  logging.debug('Checking local database')
  latest_time_period = '-' + str(LATEST_DAYS)  + ' day'
  db.execute("SELECT filename,filepath,ftp_filepath FROM latest \
   WHERE (nc_date < date('now',?) AND uploaded == ?)",[latest_time_period, UPLOADED]) 
  results_delete = db.fetchall()
  logging.debug(f'delete {len(results_delete)}; {results_delete}')
  
  dnt_delete = {}
  for item in results_delete: 
    filename, filepath_local  = item[0], item[1]
    dnt_delete[filename] = item[2]
    db.execute("UPDATE latest SET uploaded = ? \
      WHERE filename = ?", [NOT_UPLOADED, filename])
  return dnt_delete


def get_files_ready_for_upload(db,status):
  latest_time_period = '-' + str(LATEST_DAYS)  + ' day'
  db.execute("SELECT filename,filepath FROM latest \
    WHERE (nc_date >= date('now',?) \
    AND NOT uploaded == ?)",[latest_time_period, UPLOADED]) 
  results_upload = db.fetchall()    
  if len(results_upload) == 0:
    status = 2 
    logging.debug('All files already exported')
  else:
    logging.debug(f'Upload {len(results_upload)}: {results_upload}')
  return results_upload,status


def upload_to_ftp(ftp, filepath,error_msg,db):
  ''' Uploads file with location 'filepath' to an ftp-server, 
  server-location set by 'directory' parameter and config-file, 
  ftp is the ftp-connection

  returns 
  upload_result: upload_ok or file_exists
  dest_filepath: target filepath on ftp-server
  start_upload_time and stop_upload_time: timestamps of upload process
  '''
  try:
    upload_result = UPLOAD_OK # default assumption
    dnt= {}
    if filepath.endswith('.nc'):
      filename = filepath.rsplit('/',1)[-1]
      date = filename.split('_')[-1].split('.')[0]
      ftp_folder = NRT_DIR + '/' + date     

    elif filepath.endswith('.xml'):
      filename = filepath.rsplit('/',1)[-1]
      ftp_folder = DNT_DIR

    elif filepath.endswith('.txt'):
      with open(filepath,'rb') as f: 
        file_bytes = f.read() 
      filename = filepath.rsplit('/',1)[-1]
      ftp_folder = INDEX_DIR
    
    ftp_filepath = ftp_folder + '/' +  filename


    start_upload_time = datetime.datetime.now().strftime(DNT_DATETIME_FORMAT)
    if not ftp.path.isdir(ftp_folder):
      ftp.mkdir(ftp_folder)
      ftp.upload(filepath, ftp_filepath)

    elif ftp.path.isfile(ftp_filepath) & filepath.endswith('.nc'):
      upload_result = FILE_EXISTS
    else:
      ftp.upload(filepath, ftp_filepath)
    
    stop_upload_time = datetime.datetime.now().strftime(DNT_DATETIME_FORMAT)

    logging.debug(f'upload result: {upload_result}')

    if upload_result == UPLOAD_OK:
      status = UPLOADED

      update_db_new_submission(db,UPLOADED,ftp_filepath,filename)

      # create DNT-entry
      dnt = create_dnt_entry(
        ftp_filepath,start_upload_time,stop_upload_time,filename) 
    else:
      logging.debug(f'upload failed: {upload_result}')
      error_msg += f'upload failed: {upload_result}'
      upload_result = FAILED_INGESTION  

  except Exception as e:
    logging.error(f'Uploading {filepath} failed: ', exc_info=True)
    upload_result = FAILED_INGESTION      
    error_msg += 'Uploading ' + filepath + 'failed: ' + str(e)  

  return upload_result, dnt, error_msg


def get_response(ftp,dnt_filepath,folder_local):
  '''  Retrieves the status of any file uploaded to CMEMS server
  returns the string of the xml responsefile generated by the CMEMS server. 
  '''
  source = (dnt_filepath.split('.')[0]
    .replace('DNT','DNT_response') + '_response.xml')
  target = folder_local + '/' +  source.split('/')[-1]

  ftp.download(source,target)
  with open(target,'r') as response_file:
    response = response_file.read()
  return response


def abort_upload(error,ftp,NRT_DIR,db,curr_date):
  # Remove currently updated files on ftp-server
  uningested_files = clean_directory(ftp, NRT_DIR)

  db.execute("SELECT * FROM latest WHERE (dnt_file = ?)",[curr_date])
  failed_ingestion = db.fetchall()
  
  logging.debug(f'failed ingestion: \n{failed_ingestion}')
  logging.debug(f'uningested files: \n {uningested_files}')

  # Update database : set uploaded to 0 where index-file is current date
  error_msg = "failed ingestion: " + error 
  abort_upload_db(error_msg)

  db.execute(sql_req,sql_var)

  return error_msg


def evaluate_response_file(ftp,dnt_filename,folder_local,db):
  '''  Retrieves response from cmems-ftp server.
  '''
  dnt_filepath = DNT_DIR + '/' +  dnt_filename
  response_received = False
  loop_iter = 0

  logging.debug('waiting for dnt-response')
  while response_received == False and loop_iter < 50 :
    time.sleep(10)
    logging.debug('checking for dnt-response ' + str(loop_iter*10))
    try:
      cmems_response = get_response(ftp,dnt_filepath,folder_local)
      response_received = True
      logging.debug('cmems response: ' + cmems_response)
    except:
      response_received = False
      logging.debug('no response found')
    loop_iter += 1

  if response_received == False: 
    return 'No response received'
  elif 'Ingested="True"' in cmems_response: 
    return '' 
  else: #ingestion failed or partial
    rejected = re.search(
      'FileName=(.+?)RejectionReason=(.+?)Status',cmems_response)
    if rejected:
      rejected_file, rejected_reason = [rejected.group(1), rejected.group(2)]
      logging.error('Rejected: {}, {}'.format(rejected_file, rejected_reason))
      rejected_filename = rejected_file.split('/')[-1].split('.')[0]

      sql_req = "UPDATE latest SET uploaded=?,comment=? WHERE filename=?"
      sql_var = ([-1,rejected_reason, rejected_filename])
      db.execute(sql_req,sql_var)
  else:
    logging.info('All files ingested')
  logging.info({'local folder':folder_local,'dnt_filepath':dnt_filepath,'cmems-response':cmems_response})
 
  return cmems_response
  

def empty_directory(ftp):
  '''   Cleans out empty folders, checks if main directory is empty. 
  returns True when empty 
  '''
  logging.debug('Checking FTP directory')
  uningested_files = clean_directory(ftp, NRT_DIR)
  with open (NOT_INGESTED,'a+') as f:
    for item in uningested_files:
      f.write(str(datetime.datetime.now()) + ': ' + str(item) + '\n')
  if ftp.listdir(NRT_DIR):
    logging.warning('ftp-folder is not empty')
    logging.error('Previous export has failed, \
      clean up remanent files before re-exporting')
    return False
  else:
    return True


def clean_directory(ftp,NRT_DIR):
  ''' removes empty directories from ftp server '''
  uningested_files = []
  for dirpath, dirnames, files in ftp.walk(NRT_DIR+'/'):
    if not dirnames and not files and not dirpath.endswith('/latest/'):
      logging.debug(f'removing EMPTY DIRECTORY: {str(dirpath)}') 
      ftp.rmdir(dirpath)
    elif files:
      uningested_files += (
        [[('dirpath',dirpath),('dirnames',dirnames),('files',files)]])
      logging.debug(f'UNINGESTED: \
        dirpath: {dirpath}, \ndirnames: {dirnames}, \nfiles: {files}')
  return uningested_files


def create_dnt_entry(filepath_ftp,start_upload_time,stop_upload_time,filename):
  if filename.endswith('.nc'):
    local_path =  LOCAL_FOLDER + '/'
  else: local_path = ''
  entry = {'ftp_filepath':filepath_ftp, 
            'start_upload_time':start_upload_time, 
            'stop_upload_time':stop_upload_time,
            'local_filepath': local_path + filename}
  logging.debug(f'dnt entry: {entry}')
  return entry
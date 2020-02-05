###############################################################################
### THE MAIN SAILDRONE SCRIPT                                               ###
###############################################################################

### Description
# Script uses Saildrone API to download saildrone data, converts it from json
# to csv format, and shares it with QuinCe.


###----------------------------------------------------------------------------
### Import packages
###----------------------------------------------------------------------------

import os
import saildrone_module as saildrone
from datetime import datetime
import shutil
import json
import pandas as pd


###----------------------------------------------------------------------------
### Handling directories
###----------------------------------------------------------------------------

# Store path to the main script directory
script_dir = os.path.dirname(os.path.realpath(__file__))

# Create a data directory if it does not already exist
if not os.path.isdir('./data_files'):
	os.mkdir(os.path.join(script_dir,'data_files'))

# Store path to the data directory
data_dir = os.path.join(script_dir,'data_files')

# Create new archive directry with current timestamp as name
now = datetime.now()
dt_string = now.strftime("%Y%m%dT%H%M%S")
archive_path = os.path.join(data_dir, str(dt_string))
os.mkdir(archive_path)


###----------------------------------------------------------------------------
### Extract information from the config and stored_info files
###----------------------------------------------------------------------------

try:
	with open ('./config.json') as file:
		configs = json.load(file)
	drones_ignored = configs['drones_ignored']
	datasets = configs['datasets']
	col_order = configs['col_order']
	FTP = configs['FTP']
except FileNotFoundError:
	# !!! Create config file with keys, no values. Notify via slack to fill inn
	# config values. Temporary solution:
	print("Missing config file")

try:
	with open('./stored_info.json') as file:
		stored_info = json.load(file)
	next_request = stored_info['next_request']
	prev_access_list = stored_info['prev_access_list']
except FileNotFoundError:
	# !!! Create stored info template file. Notify via slack to fill inn values.
	# Temporary solution:
	print("Missing 'stored_info.json' file")


###----------------------------------------------------------------------------
### Find out which data to request
###----------------------------------------------------------------------------

# Create authentication token for saildrone API, and see what's available
token = saildrone.auth()
access_list = saildrone.get_available(token)

# If the access list has changed since the previous run (and previous
# access list was not empty): print message and replace the prev_access_list
# with the new access_list.
if prev_access_list != access_list and bool(prev_access_list):
	#!!! Send message to slack. Temp soluion:
	print("Access list has changed")
	stored_info['prev_access_list'] = access_list

# If the access list contains new drones which are not on the ignore list, add
# them to the next_request dictionary:
for dictionary in access_list:
	drone = str(dictionary['drone_id'])
	if drone not in drones_ignored and drone not in next_request.keys():
			next_request[drone] = dictionary['start_date']

# Function 'check_next_request' will return what we need to request.
next_request_checked = saildrone.check_next_request(
	next_request, access_list, datasets, drones_ignored)


###----------------------------------------------------------------------------
### Download json, convert to csv, merge datasets and send to Quince
###----------------------------------------------------------------------------

# The end date for download request are always the current time stamp
end = now.strftime("%Y-%m-%dT%H:%M:%S") + ".000Z"

# Create connection to the Quince FTP
ftpconn = saildrone.connect_ftp(FTP)

# Loop that downloads, converts, merges, and sends data files it to the QuinCe
# FTP. Keep track on what to request next time in the next_request_updated.
next_request_updated = dict(next_request_checked)
for drone_id, start in next_request_checked.items():

	# Download the json files and store their paths
	json_paths =[]
	for dataset in datasets:
		json_path = saildrone.write_json(
			data_dir, drone_id, dataset, start, end, token)
		json_paths.append(json_path)

	# Convert each json to csv. Move the json file to the archive folder. Store
	# the paths of the csv files.
	csv_paths = []
	for path in json_paths :
		csv_path = saildrone.convert_to_csv(path)
		csv_paths.append(csv_path)
		shutil.move(path, os.path.join(archive_path, os.path.basename(path)))

	# Create merged dataframe
	merged_df = saildrone.merge_datasets(csv_paths)

	# Move the individual csv files to archive
	for path in csv_paths:
		shutil.move(path, os.path.join(archive_path,
		os.path.basename(path)))

	# Add missing columns (with empty data), and sort by the defined column
	# order to ensure consistent data format
	for param in col_order:
		if param not in merged_df.columns:
			merged_df[param] = None
	# !!! Check: If there are new headers in the merged_df.
	# Notify slack and stop script
	merged_sorted_df = merged_df[col_order]

	# Get the last record we downloaded from the biogeo dataset. This will
	# change once the different SailDrone datasets are no longer merged
	# together. This will be used as the starting point for the next request.
	time_index = merged_sorted_df.columns.get_loc('time_interval_biogeFile')
	last_record_date = merged_sorted_df.tail(1).iloc[0,time_index]

	# Store the merged data as a csv file in the archive folder
	merged_file_name = (str(drone_id) + '_'
		+ start[0:4] + start[5:7] + start[8:10] + 'T' + start[11:13]
		+ start[14:16] + start[17:19] + "-"
		+ last_record_date.strftime('%Y%m%dT%H%M%S') + '.csv')
	merged_path = os.path.join(archive_path, merged_file_name)
	merged_csv = merged_sorted_df.to_csv(merged_path,
		index=None, header=True, sep=',')

	# Open the merged csv file in byte format and send to the Quince FTP
	with open(merged_path, 'rb') as file:
		byte = file.read()
		upload_result = saildrone.upload_file(ftpconn=ftpconn,
			ftp_config=FTP, instrument_id=1000, filename=merged_file_name,
			contents=byte)

	#  Set new start date for the next_request:
	next_request_updated[drone_id] = (last_record_date
		+ pd.Timedelta("1 minute")).strftime("%Y-%m-%dT%H:%M:%S.000Z")


###----------------------------------------------------------------------------
### Prepare for next download request
###----------------------------------------------------------------------------

# Update stored_info file
#stored_info['next_request'] = next_request_updated
#with open('./stored_info.json', 'w') as file:
#	json.dump(stored_info, file,
#		sort_keys=True, indent=4, separators=(',',': '))
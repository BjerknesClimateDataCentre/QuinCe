
import logging
import toml, json
from zipfile import ZipFile
from io import BytesIO
import ntpath

# Local modules
import RetrieverFactory, nrtdb, nrtftp

dbconn = None
ftpconn = None
logger = None

# Upload a file to the FTP server. If it's a ZIP file,
# extract it and upload the contents as individual files
def upload_file(ftp_config, instrument_id, filename, contents):
  upload_result = -1

  if not str.endswith(filename, ".zip"):
    upload_result = nrtftp.upload_file(ftpconn, ftp_config,
            instrument_id, filename, contents)
  else:
    with ZipFile(BytesIO(contents), 'r') as unzip:
      for name in unzip.namelist():
        log_instrument(instrument_id, logging.DEBUG, "Uploading "
          + "ZIP entry " + name)
        upload_result = nrtftp.upload_file(ftpconn, ftp_config,
          instrument_id, ntpath.basename(name), unzip.read(name))

        # If any file fails to upload, stop
        if upload_result != nrtftp.UPLOAD_OK:
          break

  return upload_result



# Log a message for a specific instrument
def log_instrument(instrument_id, level, message):
  logger.log(level, str(instrument_id) + ":" + message)

# Read in the config
with open("config.toml", "r") as config_file:
  config = toml.loads(config_file.read())

# Set up logging
logging.basicConfig(filename="nrt_collector.log",
  format="%(asctime)s:%(levelname)s:%(message)s")
logger = logging.getLogger('nrt_collector')
logger.setLevel(level=config["Logging"]["level"])

# Connect to NRT database and get instrument list
dbconn = nrtdb.get_db_conn(config["Database"]["location"])
instruments = nrtdb.get_instrument_ids(dbconn)

# Connect to FTP server
ftpconn = nrtftp.connect_ftp(config["FTP"])

# Loop through each instrument
for instrument_id in instruments:
  log_instrument(instrument_id, logging.INFO, "Processing instrument")
  instrument = nrtdb.get_instrument(dbconn, instrument_id)

  if instrument["type"] is None:
    log_instrument(instrument_id, logging.ERROR, "Configuration type not set")
  else:
    # Build the retriever
    retriever = RetrieverFactory.get_instance(instrument["type"],
      instrument_id, logger, json.loads(instrument["config"]))

    # Make sure configuration is still valid
    if not retriever.test_configuration():
      log_instrument(instrument_id, logging.ERROR, "Configuration invalid")
    # Initialise the retriever
    elif not retriever.startup():
      log_instrument(instrument_id, logging.ERROR, "Could not initialise retriever")
    else:

      # Loop through all files returned by the retriever one by one
      while retriever.load_next_file():
        for file in retriever.current_files:
          log_instrument(instrument_id, logging.DEBUG, "Uploading " + file["filename"] \
            + " to FTP server")

          upload_result = upload_file(config["FTP"], instrument_id,
            file["filename"], file["contents"])

          if upload_result == nrtftp.NOT_INITIALISED:
            log_instrument(instrument_id, logging.ERROR, "FTP not initialised")
            retriever.file_failed()
          elif upload_result == nrtftp.FILE_EXISTS:
            log_instrument(instrument_id, logging.DEBUG, "File exists on FTP "
              + "server - will retry later")
            retriever.file_not_processed()
          elif upload_result == nrtftp.UPLOAD_OK:
            log_instrument(instrument_id, logging.DEBUG, "File uploaded OK")
            retriever.file_succeeded()
          else:
            log_instrument(instrument_id, logging.CRITICAL, "Unrecognised "
              + "upload result " + str(upload_result))
            exit()

      retriever.shutdown()

if ftpconn is not None:
  ftpconn.close()

if dbconn is not None:
  dbconn.close()

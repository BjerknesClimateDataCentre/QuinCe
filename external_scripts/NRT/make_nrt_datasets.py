import logging
import urllib.error

import toml

# Local modules
import nrtdb
import quince


# Log a message for a specific instrument
def log_instrument(logger, instrument_id, level, message):
    logger.log(level, str(instrument_id) + ":" + message)


# Extract the list of IDs from a set of instruments
def get_ids(instruments):
    result = []
    for instrument in instruments:
        result.append(instrument["id"])
    return result


def main():
    db_conn = None

    try:
        with open("config.toml", "r") as config_file:
            config = toml.loads(config_file.read())

        # Blank logger - sends everything to /dev/null
        logging.basicConfig(filename="make_nrt_datasets.log",
                            format="%(asctime)s:%(levelname)s:%(message)s")
        logger = logging.getLogger('make_nrt_datasets')
        logger.setLevel(level=config["Logging"]["level"])

        db_conn = nrtdb.get_db_conn(config["Database"]["location"])

        print("Getting NRT instruments...")
        nrt_instruments = nrtdb.get_instruments(db_conn)
        nrt_ids = get_ids(nrt_instruments)

        for nrt_id in nrt_ids:
            nrt_response = quince.make_nrt_dataset(config, nrt_id)
            status_code = nrt_response.status_code
            if status_code == 200:
                log_instrument(logger, nrt_id, logging.INFO,
                               "NRT dataset created")
            elif status_code != 204:
                log_instrument(logger, nrt_id, logging.ERROR,
                               "NRT dataset failed: " + str(status_code))

    except urllib.error.HTTPError as e:
        print("%s %s" % (e.code, e.reason))
    except urllib.error.URLError as e:
        print(e)
    finally:
        if db_conn is not None:
            nrtdb.close(db_conn)


if __name__ == '__main__':
    main()

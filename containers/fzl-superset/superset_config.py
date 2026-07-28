import os


def get_env_variable(var_name, default=None):
    try:
        return os.environ[var_name]
    except KeyError:
        if default is not None:
            return default
        raise EnvironmentError(f"The environment variable {var_name} was missing")


DATABASE_DIALECT = get_env_variable("DATABASE_DIALECT", "postgresql")
DATABASE_HOST = get_env_variable("DATABASE_HOST")
DATABASE_PORT = get_env_variable("DATABASE_PORT")
DATABASE_DB = get_env_variable("DATABASE_DB")
DATABASE_USER = get_env_variable("DATABASE_USER")
DATABASE_PASSWORD = get_env_variable("DATABASE_PASSWORD")

SQLALCHEMY_DATABASE_URI = (
    f"{DATABASE_DIALECT}://"
    f"{DATABASE_USER}:{DATABASE_PASSWORD}@{DATABASE_HOST}:{DATABASE_PORT}/{DATABASE_DB}"
)

REDIS_HOST = get_env_variable("REDIS_HOST")
REDIS_PORT = get_env_variable("REDIS_PORT")

CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 300,
    "CACHE_KEY_PREFIX": "superset_",
    "CACHE_REDIS_HOST": REDIS_HOST,
    "CACHE_REDIS_PORT": REDIS_PORT,
    "CACHE_REDIS_DB": 1,
}
DATA_CACHE_CONFIG = CACHE_CONFIG

SECRET_KEY = get_env_variable("SUPERSET_SECRET_KEY")

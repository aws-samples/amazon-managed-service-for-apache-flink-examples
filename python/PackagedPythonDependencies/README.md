## WORK IN PROGRESS

Download the dependencies for the target architecture
```
pip install -r requirements.txt --target=dep/ --platform=manylinux2014_x86_64 --only-binary=:all:
```

Install the dependencies for local development, assuming you are using a venv

```
pip install -r requirements.txt
```


#### Additional configuring for PyFink application on Managed Flink

To tell Managed Flink what Python script to run and the fat-jar containing all dependencies, you need to specific some
additional Runtime Properties, as part of the application configuration:

| Group ID                              | Key       | Mandatory | Value                          | Notes                                                                              |
|---------------------------------------|-----------|-----------|--------------------------------|------------------------------------------------------------------------------------|
| `kinesis.analytics.flink.run.options` | `python`  | Y         | `main.py`                      | The Python script containing the main() method to start the job.                   |
| `kinesis.analytics.flink.run.options` | `jarfile` | Y         | `lib/pyflink-dependencies.jar` | Location (inside the zip) of the fat-jar containing all jar dependencies.          |
| `kinesis.analytics.flink.run.options` | `pyFiles` | Y         | `dep/`                         | Relative path of the subdirectory (inside the zip) containing Python dependencies. |
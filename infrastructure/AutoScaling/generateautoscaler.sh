#!/bin/bash

print_help_and_exit() {
  echo "Usage: sythautoscaler.sh [type=<metric-type>] metric=<metric-name> stat=<statistics> "
  echo "Parameters:"
  echo "  type=[KDA|MSK|Kinesis|KinesisEFO]: determines the metric type, default = KDA (AWS/KinesisAnalytics namespace metrics)"
  echo "  metric=<metric-name>: the name of the metric"
  echo "  stat=[Average|Sum|Minimum|Maximum]: determines the metric stat"
  exit 1
}


# Validate parameters
METRIC_FOUND=false
for ARG in "$@"; do
  if [[ ! "$ARG" =~ ^[a-zA-Z0-9]+=[^:]*$ ]]; then
    echo "Error: Invalid argument '$ARG'. Must be in the form <param>=<value>"
    print_help_and_exit
  fi
  if [[ "$ARG" =~ ^metric= ]]; then
    METRIC_FOUND=true
    METRIC_NAME="${ARG#metric=}"
  fi
    if [[ "$ARG" =~ ^stat= ]]; then
      STAT_FOUND=true
      STAT_NAME="${ARG#stat=}"
    fi
done

if [ "$METRIC_FOUND" = false ]; then
  echo "Missing mandatory 'metric' parameter"
  print_help_and_exit
fi
if [ "$STAT_FOUND" = false ]; then
  echo "Missing mandatory 'stat' parameter"
  print_help_and_exit
fi

# Move to subdir
CURRDIR=$(pwd)
cd cdk

# Construct the command
CMD="npx cdk synth"
for ARG in "$@"; do
  CMD+=" -c $ARG"
done

# Install node dependencies if node_modules doesn't exist or package.json has changed
if [ ! -d "node_modules" ] || [ package.json -nt node_modules ]; then
  echo "Installing dependencies..." >&2
  npm install --no-fund && echo_success "Dependencies installed"
fi

# CFN template file name
CFN_TEMPLATE_FILE="Autoscaler-${METRIC_NAME}-${STAT_NAME}.yaml"

# Execute synth command using local node dependencies
echo "Generating autoscaler CFN template..."
$CMD > "${CURRDIR}/${CFN_TEMPLATE_FILE}"
if [ $? -eq 0 ]; then
  echo "Autoscaler CFN template generated: ${CFN_TEMPLATE_FILE}"
else
  echo "Error generating autoscaler CFN template"
fi  

# Move back
cd ${CURRDIR}
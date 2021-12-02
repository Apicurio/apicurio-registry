#!/bin/bash

#############################################################################################################################################################################################
## Description: The script helps in verifying two things:-                                                                                                                                 ##
##                (1) All the images that are passed as parameters are pushed successfully to dockerhub. This is done by fetching the SHA Digests                                          ## 
##                    for all the images. If we are able to fetch the SHA Digest for the images, that means the images are available at Dockerhub                                          ## 
##                                                                                                                                                                                         ##
##                (2) All the images that are passed as parameters are same or not. This is done by matching the SHA Digests for all the images.                                           ## 
##                    If the SHA Digest for all the images are same, that means the images are same.                                                                                       ## 
##                                                                                                                                                                                         ##
##                    NOTE: I have used "Skopeo" to inspect the images. Skopeo tool can helps us in inspecting the docker images without pulling them                                      ##
##                                                                                                                                                                                         ##
## USAGE: The script accepts image names as paramaters. We can pass as many image names as we want.                                                                                        ##
##                                                                                                                                                                                         ##
## EXAMPLE: ./verify-docker-release.sh apicurio/apicurio-registry-sql:1.2.3.Final apicurio/apicurio-registry-sql:latest apicurio/apicurio-registry-sql:latest-release ## 
##                                                                                                                                                                                         ##
############################################################################################################################################################################################# 

set -euo pipefail

IMAGES=("$@")
SHA=()
i=0

# Fetching SHA Digest for the images
for image in "${IMAGES[@]}"; do
  SHA[$i]=$(skopeo inspect --raw --tls-verify=false docker://${image} | jq --raw-output '.config.digest' | sed 's/sha256://;s/"//g')
  echo "SHA for Image ${image}: ${SHA[$i]}"
  ((i=i+1))
done
echo "The Images are successfully pushed to the dockerhub. Verifying if the images are same..."


# Verifying if images are same by comparing SHA Digest of the images
watermark=${SHA[0]}
not_equal=false
for i in "${SHA[@]}"; do
    if [[ "$watermark" != "$i" ]]; then
        not_equal=true
        break
    fi
done

if [[ $not_equal = "true" ]]; then
	echo "[FAILURE] The Images Are Not Same. Verification Failed" && exit 1
else
	echo "[SUCCESS] Verification Successfull! The SHA Digest Matched for all the images."
fi





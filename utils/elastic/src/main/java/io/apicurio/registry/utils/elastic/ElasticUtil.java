package io.apicurio.registry.utils.elastic;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;


/**
 * @author vvilerio
 */
public class ElasticUtil {
   private static final Logger log = LoggerFactory.getLogger(ElasticUtil.class);

   /**
    * Convert artifactMetaDataDto To SearchedArtifactDto
    * @param amdd
    * @return SearchedArtifactDto object
    */
   public static SearchedArtifactDto artifactMetaDataDtoToSearchedArtifactDto(final ArtifactMetaDataDto amdd){
      final SearchedArtifactDto sad = new SearchedArtifactDto();
      sad.setGroupId(amdd.getGroupId());
      sad.setCreatedBy(amdd.getCreatedBy());
      sad.setState(amdd.getState());
      sad.setDescription(amdd.getDescription());
      sad.setId(amdd.getId());
      sad.setCreatedOn(new Date(amdd.getCreatedOn()));
      sad.setLabels(amdd.getLabels());
      sad.setModifiedBy(amdd.getModifiedBy());
      sad.setName(amdd.getName());
      sad.setType(amdd.getType());
      return sad;
   }

}
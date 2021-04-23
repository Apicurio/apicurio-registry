package io.apicurio.registry.storage.impl.elasticsql.pojo;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author vvilerio
 */
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Getter
@Setter
public class FullArtifactDto extends ArtifactMetaDataDto {

   private String contentHash;
   private String canonicalHash;
   private String content;
   private String version;

   public FullArtifactDto() {

   }
}

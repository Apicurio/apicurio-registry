package io.apicurio.registry.utils.description;


import org.apache.commons.lang3.StringUtils;

/**
 * @author vvilerio
 */
public class MetaTools {

   private static final String DOTS = "...";

   /**
    * limits the length of the description field
    * @param description
    * @return String description
    */
   public static String descriptionLimiter(final String description){
      // Description character varying(1024) in DataBase
      if(StringUtils.isNotBlank(description)){
         return description.length() > 1024 ? description.substring(0,1021).concat(DOTS) : description;
      }
      return description;
   }

}

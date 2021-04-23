/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.elasticsql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.ContentNotFoundException;
import io.apicurio.registry.storage.GroupAlreadyExistsException;
import io.apicurio.registry.storage.GroupNotFoundException;
import io.apicurio.registry.storage.LogConfigurationNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.elasticsql.pojo.FullArtifactDto;
import io.apicurio.registry.storage.impl.elasticsql.sql.ElasticSqlStore;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.elastic.ElasticUtil;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.json.JsonObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME_DESC;
import static io.apicurio.registry.storage.impl.sql.SqlUtil.denormalizeGroupId;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * An implementation of a registry artifactStore for ElasticSearch
 *
 * @author vvilerio
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@Counted(name = STORAGE_OPERATION_COUNT, description = STORAGE_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_COUNT})
@ConcurrentGauge(name = STORAGE_CONCURRENT_OPERATION_COUNT, description = STORAGE_CONCURRENT_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_CONCURRENT_OPERATION_COUNT})
@Timed(name = STORAGE_OPERATION_TIME, description = STORAGE_OPERATION_TIME_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_TIME}, unit = MILLISECONDS)
@Logged
@SuppressWarnings("unchecked")
public class ElasticRegistryStorage extends AbstractRegistryStorage {

   public static final String INDEX = "apicurio";
   private static final Logger log = LoggerFactory.getLogger(ElasticRegistryStorage.class);
   private static final String ELASTIC = "elasticsql";
   @Inject
   RestHighLevelClient restHighLevelClient;

   @Inject
   ElasticSqlStore sqlStore;

   @Inject
   ArtifactTypeUtilProviderFactory factory;

//    @Inject
//    TenantContext tenantContext;

   @Inject
   SecurityIdentity securityIdentity;

   private boolean bootstrapped = false;
   private boolean stopped = true;


   void onConstruct(@Observes StartupEvent ev) {
      log.info("Using Elastic-SQL artifactStore.");

   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#storageName()
    */
   @Override
   public String storageName() {
      return ELASTIC;
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#supportsMultiTenancy()
    */
   @Override
   public boolean supportsMultiTenancy() {
      return true;
   }

   /**
    * @see io.apicurio.registry.storage.impl.AbstractRegistryStorage#isReady()
    */
   @Override
   public boolean isReady() {
      return bootstrapped;
   }

   /**
    * @see io.apicurio.registry.storage.impl.AbstractRegistryStorage#isAlive()
    */
   @Override
   public boolean isAlive() {
      return bootstrapped && !stopped;
   }

   @PreDestroy
   void onDestroy() {
      stopped = true;
   }


   protected EditableArtifactMetaDataDto extractMetaData(ArtifactType artifactType, ContentHandle content) {
      ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
      ContentExtractor extractor = provider.getContentExtractor();
      ExtractedMetaData emd = extractor.extract(content);
      EditableArtifactMetaDataDto metaData;
      if (emd != null) {
         metaData = new EditableArtifactMetaDataDto(emd.getName(), emd.getDescription(), emd.getLabels(), emd.getProperties());
      } else {
         metaData = new EditableArtifactMetaDataDto();
      }
      return metaData;
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
    */
   @Override
   public CompletionStage<ArtifactMetaDataDto> createArtifact(String groupId, String artifactId, String version, ArtifactType artifactType,
                                                              ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
      return createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
    */
   @Override
   public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                                          ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
      if (sqlStore.isArtifactExists(groupId, artifactId)) {
         throw new ArtifactAlreadyExistsException(groupId, artifactId);
      }

      byte[] contentBytes = content.bytes();
      String contentHash = DigestUtils.sha256Hex(contentBytes);
      String createdBy = securityIdentity.getPrincipal().getName();
      Date createdOn = new Date();

      // Put the content in the DB and get the unique content ID back.
      long contentId = sqlStore.withHandle(handle -> {
         return sqlStore.createOrUpdateContent(handle, artifactType, content);
      });

      if (metaData == null) {
         metaData = extractMetaData(artifactType, content);
      }
      CompletionStage<ArtifactMetaDataDto> vmdd = sqlStore.createArtifactWithMetadata(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn, metaData, null);

      final EditableArtifactMetaDataDto meta = metaData;
      // create to Elastic
      vmdd.thenAccept(s -> this.createElasticArtifactWithMetadata(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn, meta, contentHash, s.getGlobalId()));

      return vmdd;
   }

   protected CompletionStage<ArtifactMetaDataDto> createElasticArtifactWithMetadata(final String groupId, final String artifactId, final String version,
                                                                                    final ArtifactType artifactType, final long contentId, final String createdBy, final Date createdOn, final EditableArtifactMetaDataDto metaData,
                                                                                    final String contentHash, final long globalId) {
      log.debug("Inserting an artifact line for: {} {}", groupId, artifactId);
      final ArtifactState state = ArtifactState.ENABLED;
      final ObjectMapper mapper = new ObjectMapper();
      final IndexRequest indexRequest = new IndexRequest(INDEX);
      indexRequest.id(artifactId);

      final FullArtifactDto fad = new FullArtifactDto();
      fad.setCreatedBy(createdBy);
      fad.setContentId(contentId);
      fad.setCreatedOn(createdOn.getTime());
      fad.setDescription(metaData.getDescription());
      fad.setLabels(metaData.getLabels());
      fad.setId(artifactId);
      fad.setState(state);
      fad.setGlobalId(globalId);
      fad.setGroupId(denormalizeGroupId(groupId));
      fad.setName(metaData.getName());
      fad.setModifiedBy(createdBy);
      fad.setType(artifactType);
      fad.setVersion(version);
      fad.setVersionId(fad.getVersionId());
      fad.setProperties(metaData.getProperties());

      //attr supp
      log.debug("setContentHash : {}", contentHash);
      fad.setContentHash(contentHash);

      try {
         String json = mapper.writeValueAsString(fad);
         indexRequest.source(json, XContentType.JSON);
         log.debug("amdd to stringJson = {}", json);
         boolean pingStatus = false;

         if (restHighLevelClient != null) {
            log.debug("restHighLevelClient is not null");
            if (restHighLevelClient.ping(RequestOptions.DEFAULT)) {
               pingStatus = true;
               log.debug("restHighLevelClient ping is {} ", pingStatus);
//                    amdd.setContentId(Long.parseLong(restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT).getId())); TODO on fait comment pour le content
               restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
            }
         }
         log.debug("restHighLevelClient indexRequest ok : {}", indexRequest);

      } catch (IOException e) {
         throw new RegistryStorageException(e.getMessage());
      }

      return CompletableFuture.completedFuture(fad);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String, java.lang.String)
    */
   @Override
   public List<String> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
      if (!sqlStore.isArtifactExists(groupId, artifactId)) {
         throw new ArtifactNotFoundException(groupId, artifactId);
      }
      sqlStore.deleteArtifact(groupId, artifactId);


      final DeleteRequest deleteRequest = new DeleteRequest(INDEX, artifactId);

      try {
         final DeleteResponse response = restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);

         if (response.getResult() == DocWriteResponse.Result.NOT_FOUND) {
            log.error("Index removal failed - document not found by id: {}", artifactId);
         }
      } catch (IOException e) {
         throw new RegistryStorageException(e.getMessage());
      }
      return new ArrayList<String>();//TODO empty list not cool
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifacts(java.lang.String)
    */
   @Override
   public void deleteArtifacts(String groupId) throws RegistryStorageException {

      if (!sqlStore.isArtifactExists(groupId)) {
         throw new ArtifactNotFoundException(groupId);
      }

      sqlStore.deleteArtifacts(groupId);

      final DeleteByQueryRequest deleteRequest = new DeleteByQueryRequest(INDEX);
      try {
         deleteRequest.setQuery(new TermQueryBuilder("groupId", groupId));
         final BulkByScrollResponse response = restHighLevelClient.deleteByQuery(deleteRequest, RequestOptions.DEFAULT);

         if (!response.isFragment()) {
            log.error("Index removal failed - document not found by groupId: {}", groupId);
         }
      } catch (IOException e) {
         throw new RegistryStorageException(e.getMessage());
      }

   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String, java.lang.String)
    */
   @Override
   public StoredArtifactDto getArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
      return sqlStore.getArtifact(groupId, artifactId);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactByContentId(long)
    */
   @Override
   public ContentHandle getArtifactByContentId(long contentId) throws ContentNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactByContentId(contentId);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactByContentHash(java.lang.String)
    */
   @Override
   public ContentHandle getArtifactByContentHash(String contentHash) throws ContentNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactByContentHash(contentHash);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
    */
   @Override
   public CompletionStage<ArtifactMetaDataDto> updateArtifact(String groupId, String artifactId, String version, ArtifactType artifactType,
                                                              ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
      return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactWithMetadata(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
    */
   @Override
   public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                                          ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {

      //TODO
//      String contentHash = ensureContent(content, groupId, artifactId, artifactType);
//      String createdBy = securityIdentity.getPrincipal().getName();
//      Date createdOn = new Date();
//
//      if (metaData == null) {
//         metaData = extractMetaData(artifactType, content);
//      }
//
//      long globalId = nextClusterGlobalId();
//
//      return submitter
//              .submitArtifact(tenantContext.tenantId(), groupId, artifactId, version, ActionType.Update, globalId, artifactType, contentHash, createdBy, createdOn, metaData)
//              .thenCompose(reqId -> (CompletionStage<ArtifactMetaDataDto>) coordinator.waitForResponse(reqId));
      return null;
   }


   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactIds(java.lang.Integer)
    */
   @Override
   public Set<String> getArtifactIds(Integer limit) {
      return sqlStore.getArtifactIds(limit);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#searchArtifacts(java.util.Set, io.apicurio.registry.storage.dto.OrderBy, io.apicurio.registry.storage.dto.OrderDirection, int, int)
    */
   @Override
   public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, int offset, int limit) {

      SearchHits hits = null;
      List<SearchedArtifactDto> results = null;
      boolean idcIndexIsPresent = true;

      try {
         SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
         String[] fields = null;
         for (final SearchFilter filter : filters) {
            switch (filter.getType()) {
               case description:
                  fields = new String[]{filter.getType().name()};
                  results = search(filter.getValue(), fields);
                  break;
               case everything:
                  fields = new String[]{SearchFilterType.name.name(), SearchFilterType.group.name(), SearchFilterType.description.name(), SearchFilterType.labels.name(), "id"};
                  results = search(filter.getValue(), fields);
                  break;
               case labels:
                  fields = new String[]{"labels"};
                  results = search(filter.getValue(), fields);
                  break;
               case name:
//                       log.debug("Switch case name : {}, value : {}", filter.getType().name(), filter.getValue());
//                        searchSourceBuilder.query(QueryBuilders.multiMatchQuery(filter.getType().name(), filter.getValue()));
//                       searchSourceBuilder.query(QueryBuilders.multiMatchQuery(filter.getValue(), new String[]{filter.getType().name(), "id"}));
                  fields = new String[]{filter.getType().name(), "id"};
                  results = search(filter.getValue(), fields);
                  break;
               case group:
                  fields = new String[]{"groupId"};
                  results = search(filter.getValue(), fields);
                  break;
               case contentHash:
                  fields = new String[]{"contentHash"};
                  results = search(filter.getValue(), fields);
                  break;
               case canonicalHash:
                  //TODO sqlStore.canonicalizeContent : where, how
                  break;
               case properties:
                  //TODO properties is a list
//                  fields = Arrays.stream(SearchFilterType.values()).map(it -> it.name()).toArray(String[]::new);
                  fields = new String[]{"properties"};
                  results = search(filter.getValue(), fields);
                  break;
               default:
                  fields = new String[]{SearchFilterType.name.name()};
                  results = search(filter.getValue(), fields);
                  break;
            }
         }
      } catch (ElasticsearchException ese) {
         log.warn(ese.getMessage());
         idcIndexIsPresent = false;
      }

      // if ElasticSearch Empty then sqlStore
      if (!idcIndexIsPresent || Objects.isNull(results) || results.isEmpty()) {
         log.debug("idcIndexIsPresent : false");
         return sqlStore.searchArtifacts(filters, orderBy, orderDirection, offset, limit);
      }

      final ArtifactSearchResultsDto finalRes =  new ArtifactSearchResultsDto();
      finalRes.setArtifacts(results);
      finalRes.setCount(results.size());
      log.debug("ArtifactSearchResultsDto", results.toString());
      return finalRes;
   }


   /**
    * search method witch Elastic
    *
    * @param value
    * @param fields
    * @return results
    */
   private List<SearchedArtifactDto> search(final String value, final String[] fields) throws ElasticsearchException {
      log.debug("search for fields {} ,value : {}",fields,value);

      try {
         if (!Objects.isNull(fields)) {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            List<SearchedArtifactDto> results = null;
            SearchRequest searchRequest = new SearchRequest(INDEX);
            if (fields.length > 1) {
               log.debug("multiMatchQuery for fields {} ,value : {}",fields,value);
               searchSourceBuilder.query(QueryBuilders.multiMatchQuery(value, fields));
            } else {
               log.debug("matchQuery for fields {} ,value : {}",fields,value);
               //findFirst
               searchSourceBuilder.query(QueryBuilders.matchQuery(fields[0], value));
//               searchSourceBuilder.fetchField()
            }
            if(!Objects.isNull(searchSourceBuilder)) {
               searchRequest.source(searchSourceBuilder);
               SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
               SearchHits hits = searchResponse.getHits();
               results = new ArrayList<>(hits.getHits().length);
               final ArtifactSearchResultsDto asrd = new ArtifactSearchResultsDto();
               for (SearchHit hit : hits.getHits()) {
                  String sourceAsString = hit.getSourceAsString();
                  JsonObject json = new JsonObject(sourceAsString);
                  log.debug("JSON : {}", json.toString());
                  final ArtifactMetaDataDto amdd = json.mapTo(FullArtifactDto.class);
                  final SearchedArtifactDto sad = ElasticUtil.artifactMetaDataDtoToSearchedArtifactDto(amdd);
                  results.add(sad);
               }
               log.debug("SearchedArtifactDtos :{}", results);
               return results;
            }
         }
      } catch (IOException e) {
         log.error(e.getMessage());
      }
      log.debug("SearchedArtifactDtos is empty");
      return new ArrayList<>();
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String, java.lang.String)
    */
   @Override
   public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactMetaData(groupId, artifactId);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
    */
   @Override
   public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, boolean canonical, ContentHandle content)
           throws ArtifactNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactVersionMetaData(groupId, artifactId, canonical, content);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(long)
    */
   @Override
   public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactMetaData(id);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
    */
   @Override
   public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
           throws ArtifactNotFoundException, RegistryStorageException {
      // Note: the next line will throw ArtifactNotFoundException if the artifact does not exist, so there is no need for an extra check.
      ArtifactMetaDataDto metaDataDto = sqlStore.getArtifactMetaData(groupId, artifactId);

   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String, java.lang.String)
    */
   @Override
   public List<RuleType> getArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactRules(groupId, artifactId);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRuleAsync(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
    */
   @Override
   public CompletionStage<Void> createArtifactRuleAsync(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
           throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
      return new CompletableFuture<>();
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String, java.lang.String)
    */
   @Override
   public void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
      if (!sqlStore.isArtifactExists(groupId, artifactId)) {
         throw new ArtifactNotFoundException(groupId, artifactId);
      }
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
    */
   @Override
   public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException,
           RuleNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactRule(groupId, artifactId, rule);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
    */
   @Override
   public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
           throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
      if (!sqlStore.isArtifactRuleExists(groupId, artifactId, rule)) {
         throw new RuleNotFoundException(rule);
      }

   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
    */
   @Override
   public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException,
           RuleNotFoundException, RegistryStorageException {
      if (!sqlStore.isArtifactRuleExists(groupId, artifactId, rule)) {
         throw new RuleNotFoundException(rule);
      }

   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersions(java.lang.String, java.lang.String)
    */
   @Override
   public List<String> getArtifactVersions(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactVersions(groupId, artifactId);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#searchVersions(java.lang.String, java.lang.String, int, int)
    */
   @Override
   public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit)
           throws ArtifactNotFoundException, RegistryStorageException {
      return sqlStore.searchVersions(groupId, artifactId, offset, limit);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(long)
    */
   @Override
   public StoredArtifactDto getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactVersion(id);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
    */
   @Override
   public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version)
           throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactVersion(groupId, artifactId, version);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
    */
   @Override
   public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, String version)
           throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
      return sqlStore.getArtifactVersionMetaData(groupId, artifactId, version);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
    */
   @Override
   public void deleteArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException,
           VersionNotFoundException, RegistryStorageException {
      sqlStore.deleteArtifactVersion(groupId, artifactId, version);
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
    */
   @Override
   public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData)
           throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
    */
   @Override
   public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
           throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
      sqlStore.deleteArtifactVersionMetaData(groupId,artifactId,version);
      //TODO elastic
   }

   /**
    * Fetches the meta data for the given artifact version, validates the state (optionally), and then calls back the handler
    * with the metadata.  If the artifact is not found, this will throw an exception.
    *
    * @param groupId
    * @param artifactId
    * @param version
    * @param states
    * @param handler
    * @throws ArtifactNotFoundException
    * @throws RegistryStorageException
    */
   private <T> T handleVersion(String groupId, String artifactId, String version, EnumSet<ArtifactState> states, Function<ArtifactVersionMetaDataDto, T> handler)
           throws ArtifactNotFoundException, RegistryStorageException {

      ArtifactVersionMetaDataDto metadata = sqlStore.getArtifactVersionMetaData(groupId, artifactId, version);

      ArtifactState state = metadata.getState();
      ArtifactStateExt.validateState(states, state, groupId, artifactId, version);
      return handler.apply(metadata);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
    */
   @Override
   public List<RuleType> getGlobalRules() throws RegistryStorageException {
      return sqlStore.getGlobalRules();
   }


   /**
    * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRules()
    */
   @Override
   public void deleteGlobalRules() throws RegistryStorageException {
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRule(io.apicurio.registry.types.RuleType)
    */
   @Override
   public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
      return sqlStore.getGlobalRule(rule);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
    */
   @Override
   public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
      if (!sqlStore.isGlobalRuleExists(rule)) {
         throw new RuleNotFoundException(rule);
      }

   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
    */
   @Override
   public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
      if (!sqlStore.isGlobalRuleExists(rule)) {
         throw new RuleNotFoundException(rule);
      }

   }

   private void updateArtifactState(ArtifactState currentState, String groupId, String artifactId, String version, ArtifactState newState, EditableArtifactMetaDataDto metaData) {
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#importData(io.apicurio.registry.storage.impexp.EntityInputStream)
    */
   @Override
   public void importData(EntityInputStream entities) throws RegistryStorageException {
      try {
         Entity entity = null;
         while ((entity = entities.nextEntity()) != null) {
            if (entity != null) {
               importEntity(entity);
            }
         }

         // Because importing just pushes a bunch of Kafka messages, we may need to
         // wait for a few seconds before we send the reset messages.  Due to partitioning,
         // we can't guarantee ordering of these next two messages, and we NEED them to
         // be consumed after all the import messages.
         try {
            Thread.sleep(2000);
         } catch (Exception e) {
         }

         // Make sure the contentId sequence is set high enough
         resetContentId();

         // Make sure the globalId sequence is set high enough
         resetGlobalId();
      } catch (IOException e) {
         throw new RegistryStorageException(e);
      }
   }

   protected void importEntity(Entity entity) throws RegistryStorageException {
      switch (entity.getEntityType()) {
         case ArtifactRule:
            importArtifactRule((ArtifactRuleEntity) entity);
            break;
         case ArtifactVersion:
            importArtifactVersion((ArtifactVersionEntity) entity);
            break;
         case Content:
            importContent((ContentEntity) entity);
            break;
         case GlobalRule:
            importGlobalRule((GlobalRuleEntity) entity);
            break;
         case Group:
            importGroup((GroupEntity) entity);
            break;
         default:
            throw new RegistryStorageException("Unhandled entity type during import: " + entity.getEntityType());
      }
   }

   protected void importArtifactRule(ArtifactRuleEntity entity) {
      RuleConfigurationDto config = new RuleConfigurationDto(entity.configuration);
//        submitter.submitArtifactRule(tenantContext.tenantId(), entity.groupId, entity.artifactId, entity.type, ActionType.Import, config);
   }

   protected void importArtifactVersion(ArtifactVersionEntity entity) {
      EditableArtifactMetaDataDto metaData = EditableArtifactMetaDataDto.builder()
              .name(entity.name)
              .description(entity.description)
              .labels(entity.labels)
              .properties(entity.properties)
              .build();
      //TODO elastic
   }

   protected void importContent(ContentEntity entity) {
      //TODO elastic
   }

   protected void importGlobalRule(GlobalRuleEntity entity) {
      RuleConfigurationDto config = new RuleConfigurationDto(entity.configuration);
      //TODO elastic
   }

   protected void importGroup(GroupEntity entity) {
      GroupEntity e = entity;
      GroupMetaDataDto group = new GroupMetaDataDto();
      group.setArtifactsType(e.artifactsType);
      group.setCreatedBy(e.createdBy);
      group.setCreatedOn(e.createdOn);
      group.setDescription(e.description);
      group.setGroupId(e.groupId);
      group.setModifiedBy(e.modifiedBy);
      group.setModifiedOn(e.modifiedOn);
      group.setProperties(e.properties);
      //TODO elastic
   }

   private void resetContentId() {
      //TODO elastic
   }

   private void resetGlobalId() {
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
    */
   @Override
   public void updateArtifactState(String groupId, String artifactId, ArtifactState state) throws ArtifactNotFoundException, RegistryStorageException {
      ArtifactMetaDataDto metadata = sqlStore.getArtifactMetaData(groupId, artifactId);
      EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
      metaDataDto.setName(metadata.getName());
      metaDataDto.setDescription(metadata.getDescription());
      metaDataDto.setLabels(metadata.getLabels());
      metaDataDto.setProperties(metadata.getProperties());
      updateArtifactState(metadata.getState(), groupId, artifactId, metadata.getVersion(), state, metaDataDto);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
    */
   @Override
   public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
      sqlStore.createGlobalRule(rule,config);
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
    */
   @Override
   public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
           throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
      ArtifactVersionMetaDataDto metadata = sqlStore.getArtifactVersionMetaData(groupId, artifactId, version);
      EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
      metaDataDto.setName(metadata.getName());
      metaDataDto.setDescription(metadata.getDescription());
      metaDataDto.setLabels(metadata.getLabels());
      metaDataDto.setProperties(metadata.getProperties());
      updateArtifactState(metadata.getState(), groupId, artifactId, version, state, metaDataDto);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getLogConfiguration(java.lang.String)
    */
   @Override
   public LogConfigurationDto getLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
      return this.sqlStore.getLogConfiguration(logger);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#listLogConfigurations()
    */
   @Override
   public List<LogConfigurationDto> listLogConfigurations() throws RegistryStorageException {
      return this.sqlStore.listLogConfigurations();
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#removeLogConfiguration(java.lang.String)
    */
   @Override
   public void removeLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
      LogConfigurationDto dto = new LogConfigurationDto();
      dto.setLogger(logger);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#setLogConfiguration(io.apicurio.registry.storage.dto.LogConfigurationDto)
    */
   @Override
   public void setLogConfiguration(LogConfigurationDto logConfiguration) throws RegistryStorageException {
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
    */
   @Override
   public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#updateGroupMetaData(io.apicurio.registry.storage.dto.GroupMetaDataDto)
    */
   @Override
   public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException {
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
    */
   @Override
   public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
      //TODO elastic
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getGroupIds(java.lang.Integer)
    */
   @Override
   public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
      return sqlStore.getGroupIds(limit);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getGroupMetaData(java.lang.String)
    */
   @Override
   public GroupMetaDataDto getGroupMetaData(String groupId) throws GroupNotFoundException, RegistryStorageException {
      return sqlStore.getGroupMetaData(groupId);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionsByContentId(long)
    */
   @Override
   public List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId) {
      return sqlStore.getArtifactVersionsByContentId(contentId);
   }

   /**
    * @see io.apicurio.registry.storage.RegistryStorage#exportData(Function)
    */
   @Override
   public void exportData(Function<Entity, Void> handler) throws RegistryStorageException {
      sqlStore.exportData(handler);
   }


}
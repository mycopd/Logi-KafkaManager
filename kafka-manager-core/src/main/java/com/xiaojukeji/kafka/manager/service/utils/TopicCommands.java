package com.xiaojukeji.kafka.manager.service.utils;

import com.xiaojukeji.kafka.manager.common.constant.Constant;
import com.xiaojukeji.kafka.manager.common.entity.ResultStatus;
import com.xiaojukeji.kafka.manager.common.entity.pojo.ClusterDO;
import kafka.admin.AdminOperationException;
import kafka.admin.AdminUtils;
import kafka.admin.BrokerMetadata;
import kafka.common.TopicAndPartition;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.security.JaasUtils;
import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.Seq;

import java.util.*;

/**
 * @author zengqiao
 * @date 20/4/22
 */
public class TopicCommands {
    public static ResultStatus createTopic(ClusterDO clusterDO,
                                           String topicName,
                                           Integer partitionNum,
                                           Integer replicaNum,
                                           List<Integer> brokerIdList,
                                           Properties config) {
        ZkUtils zkUtils = null;
        try {
            zkUtils = ZkUtils.apply(
                    clusterDO.getZookeeper(),
                    Constant.DEFAULT_SESSION_TIMEOUT_UNIT_MS,
                    Constant.DEFAULT_SESSION_TIMEOUT_UNIT_MS,
                    JaasUtils.isZkSecurityEnabled()
            );

            // 生成分配策略
            scala.collection.Map<Object, scala.collection.Seq<Object>> replicaAssignment =
                    AdminUtils.assignReplicasToBrokers(
                            convert2BrokerMetadataSeq(brokerIdList),
                            partitionNum,
                            replicaNum,
                            randomFixedStartIndex(),
                            -1
            );

            // 写ZK
            AdminUtils.createOrUpdateTopicPartitionAssignmentPathInZK(
                    zkUtils,
                    topicName,
                    replicaAssignment,
                    config,
                    false
            );
        } catch (NullPointerException e) {
            return ResultStatus.TOPIC_OPERATION_PARAM_NULL_POINTER;
        } catch (InvalidPartitionsException e) {
            return ResultStatus.TOPIC_OPERATION_PARTITION_NUM_ILLEGAL;
        } catch (InvalidReplicationFactorException e) {
            return ResultStatus.BROKER_NUM_NOT_ENOUGH;
        } catch (TopicExistsException | ZkNodeExistsException e) {
            return ResultStatus.TOPIC_OPERATION_TOPIC_EXISTED;
        } catch (InvalidTopicException e) {
            return ResultStatus.TOPIC_OPERATION_TOPIC_NAME_ILLEGAL;
        } catch (Throwable t) {
            return ResultStatus.TOPIC_OPERATION_UNKNOWN_ERROR;
        } finally {
            if (zkUtils != null) {
                zkUtils.close();
            }
        }
        return ResultStatus.SUCCESS;
    }

    public static ResultStatus deleteTopic(ClusterDO clusterDO, String topicName) {
        ZkUtils zkUtils = null;
        try {
            zkUtils = ZkUtils.apply(
                    clusterDO.getZookeeper(),
                    Constant.DEFAULT_SESSION_TIMEOUT_UNIT_MS,
                    Constant.DEFAULT_SESSION_TIMEOUT_UNIT_MS,
                    JaasUtils.isZkSecurityEnabled()
            );
            AdminUtils.deleteTopic(zkUtils, topicName);
        } catch (UnknownTopicOrPartitionException e) {
            return ResultStatus.TOPIC_OPERATION_UNKNOWN_TOPIC_PARTITION;
        } catch (ZkNodeExistsException e) {
            return ResultStatus.TOPIC_OPERATION_TOPIC_IN_DELETING;
        } catch (Throwable t) {
            return ResultStatus.TOPIC_OPERATION_UNKNOWN_ERROR;
        } finally {
            if (zkUtils != null) {
                zkUtils.close();
            }
        }
        return ResultStatus.SUCCESS;
    }

    public static ResultStatus modifyTopicConfig(ClusterDO clusterDO, String topicName, Properties config) {
        ZkUtils zkUtils = null;
        try {
            zkUtils = ZkUtils.apply(
                    clusterDO.getZookeeper(),
                    Constant.DEFAULT_SESSION_TIMEOUT_UNIT_MS,
                    Constant.DEFAULT_SESSION_TIMEOUT_UNIT_MS,
                    JaasUtils.isZkSecurityEnabled()
            );

            AdminUtils.changeTopicConfig(zkUtils, topicName, config);
        } catch (AdminOperationException e) {
            return ResultStatus.TOPIC_OPERATION_UNKNOWN_TOPIC_PARTITION;
        } catch (InvalidConfigurationException e) {
            return ResultStatus.TOPIC_OPERATION_TOPIC_CONFIG_ILLEGAL;
        } catch (Throwable t) {
            return ResultStatus.TOPIC_OPERATION_UNKNOWN_ERROR;
        } finally {
            if (zkUtils != null) {
                zkUtils.close();
            }
        }

        return ResultStatus.SUCCESS;
    }

    public static ResultStatus expandTopic(ClusterDO clusterDO,
                                           String topicName,
                                           Integer partitionNum,
                                           List<Integer> brokerIdList) {
        ZkUtils zkUtils = null;
        try {
            zkUtils = ZkUtils.apply(
                    clusterDO.getZookeeper(),
                    Constant.DEFAULT_SESSION_TIMEOUT_UNIT_MS,
                    Constant.DEFAULT_SESSION_TIMEOUT_UNIT_MS,
                    JaasUtils.isZkSecurityEnabled()
            );

            // 已有分区的分配策略
            scala.collection.mutable.Map<TopicAndPartition, Seq<Object>> existingAssignScalaMap =
                    zkUtils.getReplicaAssignmentForTopics(JavaConversions.asScalaBuffer(Arrays.asList(topicName)));


            // 新增分区的分配策略
            Map<Object, Seq<Object>> newAssignMap = JavaConversions.asJavaMap(
                    AdminUtils.assignReplicasToBrokers(
                            convert2BrokerMetadataSeq(brokerIdList),
                            partitionNum,
                            existingAssignScalaMap.head()._2().size(),
                            randomFixedStartIndex(),
                            existingAssignScalaMap.size()
                    )
            );

            Map<TopicAndPartition, scala.collection.Seq<Object>> existingAssignJavaMap =
                    JavaConversions.asJavaMap(existingAssignScalaMap);
            // 新增分区的分配策略和旧的分配策略合并
            Map<Object, Seq<Object>> targetMap = new HashMap<>();
            for (Map.Entry<TopicAndPartition, Seq<Object>> entry : existingAssignJavaMap.entrySet()) {
                targetMap.put(entry.getKey().partition(), entry.getValue());
            }
            for (Map.Entry<Object, Seq<Object>> entry : newAssignMap.entrySet()) {
                targetMap.put(entry.getKey(), entry.getValue());
            }

            // 更新ZK上的assign
            AdminUtils.createOrUpdateTopicPartitionAssignmentPathInZK(
                    zkUtils,
                    topicName,
                    JavaConversions.asScalaMap(targetMap),
                    null,
                    true
            );
        } catch (Throwable t) {
            return ResultStatus.TOPIC_OPERATION_UNKNOWN_ERROR;
        } finally {
            if (zkUtils != null) {
                zkUtils.close();
            }
        }

        return ResultStatus.SUCCESS;
    }

    private static Seq<BrokerMetadata> convert2BrokerMetadataSeq(List<Integer> brokerIdList) {
        List<BrokerMetadata> brokerMetadataList = new ArrayList<>();
        for (Integer brokerId: brokerIdList) {
            brokerMetadataList.add(new BrokerMetadata(brokerId, Option.<String>empty()));
        }
        return JavaConversions.asScalaBuffer(brokerMetadataList).toSeq();
    }

    /**
     * 生成一个伪随机数, 即随机选择一个起始位置的Broker
     */
    private static int randomFixedStartIndex() {
        return (int) System.currentTimeMillis() % 1013;
    }
}
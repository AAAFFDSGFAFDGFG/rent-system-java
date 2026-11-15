package com.example.rentsystemjava.repository;

import com.example.rentsystemjava.domain.House;
import com.example.rentsystemjava.domain.enums.HouseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 房源数据层。软删除统一口径：所有业务查询必须带 deleted_at IS NULL（本接口封装，禁止绕过）。
 */
public interface HouseRepository extends JpaRepository<House, Long> {

    Optional<House> findByIdAndDeletedAtIsNull(Long id);

    List<House> findAllByDeletedAtIsNull();

    List<House> findAllByDeletedAtIsNullAndStatus(HouseStatus status);

    /** 小区下拉：DISTINCT community */
    @Query("select distinct h.community from House h where h.deletedAt is null order by h.community")
    List<String> findDistinctCommunities();

    /** 楼栋级联：指定小区下的 DISTINCT building */
    @Query("select distinct h.building from House h where h.deletedAt is null and h.community = :community order by h.building")
    List<String> findDistinctBuildings(@Param("community") String community);

    long countByDeletedAtIsNullAndStatus(HouseStatus status);

    long countByDeletedAtIsNull();

    /** 位置唯一预检（新增用）：未删除房源中同小区/楼栋/楼层/房号是否已存在 */
    boolean existsByCommunityAndBuildingAndFloorNoAndRoomNoAndDeletedAtIsNull(
            String community, String building, Short floorNo, String roomNo);

    /** 位置唯一预检（编辑用，排除自身） */
    @Query("""
            select count(h) > 0 from House h
            where h.deletedAt is null
              and h.community = :community and h.building = :building
              and h.floorNo = :floorNo and h.roomNo = :roomNo
              and h.id <> :id
            """)
    boolean existsSameLocationExcludingId(@Param("id") Long id,
                                          @Param("community") String community,
                                          @Param("building") String building,
                                          @Param("floorNo") Short floorNo,
                                          @Param("roomNo") String roomNo);
}

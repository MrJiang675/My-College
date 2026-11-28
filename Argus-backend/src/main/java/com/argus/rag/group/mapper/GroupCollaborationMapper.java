package com.argus.rag.group.mapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface GroupCollaborationMapper {
    /** Membership-scoped aggregate; pending requests are visible only to the owner. */
    @Select("""
        select g.id as "groupId",
          (select count(*) from group_memberships m where m.group_id=g.id) as "memberCount",
          (select count(*) from documents d where d.group_id=g.id and d.deleted=false) as "documentCount",
          (select count(*) from documents d where d.group_id=g.id and d.deleted=false and d.status='READY') as "readyCount",
          case when g.owner_user_id=#{userId} then
            (select count(*) from group_join_requests r where r.group_id=g.id and r.status='PENDING') else 0 end as "pendingRequestCount"
        from groups g where g.status='ACTIVE' and exists
          (select 1 from group_memberships mine where mine.group_id=g.id and mine.user_id=#{userId})
        order by g.id
        """)
    List<Map<String,Object>> statistics(long userId);
}

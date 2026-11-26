package com.argus.rag.college.mapper;

import com.argus.rag.college.Workspace;
import org.apache.ibatis.annotations.*;

public interface WorkspaceMapper {
    @Select("SELECT revision, payload FROM college_workspace WHERE owner_id=#{id}")
    Workspace load(long id);

    @Insert("INSERT INTO college_workspace(owner_id,revision,payload) VALUES(#{id},1,#{payload}) ON CONFLICT DO NOTHING")
    int create(@Param("id") long id, @Param("payload") String payload);

    @Update("UPDATE college_workspace SET payload=#{payload},revision=revision+1 WHERE owner_id=#{id} AND revision=#{revision}")
    int update(@Param("id") long id, @Param("revision") long revision, @Param("payload") String payload);
}

package com.aiworkspace.workspaces.mappers;

import com.aiworkspace.workspaces.entities.WorkspaceEntity;
import com.aiworkspace.workspaces.models.Workspace;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

    Workspace toModel(WorkspaceEntity entity);
}

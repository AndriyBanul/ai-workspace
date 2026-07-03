package com.aiworkspace.workspaces.mappers;

import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.repositories.WorkspaceEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

    Workspace toModel(WorkspaceEntity entity);
}

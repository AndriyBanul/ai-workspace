package com.aiworkspace.users.mappers;

import com.aiworkspace.users.entities.UserAccountEntity;
import com.aiworkspace.users.models.UserAccount;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserAccountMapper {

    UserAccount toModel(UserAccountEntity entity);
}

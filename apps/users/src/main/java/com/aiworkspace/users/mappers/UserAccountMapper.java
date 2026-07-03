package com.aiworkspace.users.mappers;

import com.aiworkspace.users.models.UserAccount;
import com.aiworkspace.users.repositories.UserAccountEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserAccountMapper {

    UserAccount toModel(UserAccountEntity entity);
}

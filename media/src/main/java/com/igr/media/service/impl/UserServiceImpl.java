package com.igr.media.service.impl;


import com.igr.media.dto.NewPassword;
import com.igr.media.dto.StatusFriend;
import com.igr.media.dto.UserDto;
import com.igr.media.entity.Friend;
import com.igr.media.entity.UserEntity;
import com.igr.media.exception.ElemNotFound;
import com.igr.media.exception.SecurityAccessException;
import com.igr.media.loger.FormLogInfo;
import com.igr.media.mapper.UserMapper;
import com.igr.media.repository.FriendsRepository;
import com.igr.media.repository.PostReadingRepository;
import com.igr.media.repository.UserRepository;
import com.igr.media.service.UserService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

/**
 * Сервис пользователей
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final FriendsRepository friendsRepository;
  private final SecurityService securityService;
  private final PostReadingRepository postReadingRepository;

  @Value("${image.user.dir.path}")
  private String userPhotoDir;

  public UserServiceImpl(UserRepository userRepository, UserMapper userMapper, FriendsRepository friendsRepository, SecurityService securityService, PostReadingRepository postReadingRepository) {
    this.userRepository = userRepository;
    this.userMapper = userMapper;
    this.friendsRepository = friendsRepository;
    this.securityService = securityService;
    this.postReadingRepository = postReadingRepository;
  }

  public UserEntity getByLogin(@NonNull String login) {
    return userRepository.findByName(login).orElseThrow(ElemNotFound::new);
  }
  /**
   * Получить данные пользователя
   */
  @Override
  public UserDto getUser(Authentication authentication) {
    log.info(FormLogInfo.getInfo());
    if (!securityService.checkAuthorRoleFromAuthentication(authentication)){ new SecurityAccessException();}
    String nameEmail = authentication.getName();
    UserEntity userEntity = findEntityByEmail(nameEmail);
    return userMapper.toDTO(userEntity);
  }

  /**
   * Обновить данные пользователя
   */
  @Override
  public UserDto updateUser(UserDto newUserDto, Authentication authentication) {
    log.info(FormLogInfo.getInfo());
    if (!securityService.checkAuthorRoleFromAuthentication(authentication)
    ||!securityService.isAuthorAuthenticated(newUserDto.getId(),authentication)){ new SecurityAccessException();}
    UserEntity user = userRepository.findByEmail(authentication.getName()).orElseThrow(ElemNotFound::new);
    newUserDto.setId(user.getId());

    userRepository.save(userMapper.toEntity(newUserDto));

    return newUserDto;
  }
  /**
   * Удалить данные пользователя
   */
  @Override
  public void deleteUser(UserDto userDto, Authentication authentication) {
    log.info(FormLogInfo.getInfo());
    if (!securityService.checkAuthorRoleFromAuthentication(authentication)
            ||!securityService.isAuthorAuthenticated(userDto.getId(),authentication)){ new SecurityAccessException();}
    UserEntity user = userRepository.findByEmail(authentication.getName()).orElseThrow(ElemNotFound::new);
    userRepository.deleteById(user.getId());
    // Удаление подписок
    friendsRepository.deleteAllByUser1(user.getId());
    postReadingRepository.deleteAllByUser_id(user.getId());
  }

  /**
   * Установить пароль пользователя
   */
  @Override
  public NewPassword setPassword(NewPassword newPassword) {
    log.info(FormLogInfo.getInfo());
    return null;
  }

  /**
   * загрузить аватарку пользователя
   */
  @Override
  public void updateUserImage(MultipartFile image, Authentication authentication) {
    log.info(FormLogInfo.getInfo());

    String nameEmail = authentication.getName();
    UserEntity userEntity = findEntityByEmail(nameEmail);
    String linkToGetImage = "/users" + "/" + userEntity.getId();
    Path filePath = Path.of(userPhotoDir,
        Objects.requireNonNull(String.valueOf(userEntity.getId())));

    if(userEntity.getImage() != null){
      try {
        Files.deleteIfExists(filePath);
        userEntity.setImage(null);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

    }
    try {
      Files.createDirectories(filePath.getParent());
      Files.deleteIfExists(filePath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try (InputStream is = image.getInputStream();
        OutputStream os = Files.newOutputStream(filePath, CREATE_NEW);
        BufferedInputStream bis = new BufferedInputStream(is, 1024);
        BufferedOutputStream bos = new BufferedOutputStream(os, 1024)) {

      bis.transferTo(bos);

      userEntity.setImage(linkToGetImage);
      userRepository.save(userEntity);

    } catch (Exception e) {
      log.info("Ошибка сохранения файла");
    }

  }

  /**
   * получить фото пользователя
   *
   * @return фото конвертированная в массив байтов
   */

  public byte[] getPhotoById(Integer id) {
    String linkToGetImage = "user_photo_dir/" + id;
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(Paths.get(linkToGetImage));
    } catch (IOException e) {
      log.info(FormLogInfo.getCatch());
      throw new RuntimeException(e);
    }
    return bytes;
  }

  /**
   * найти пользователя по id
   *
   * @param id id пользователя
   * @return пользователь
   */
  @Override
  public UserDto findById(int id,Authentication authentication) {
    log.info(FormLogInfo.getInfo());
    if (!securityService.checkAuthorRoleFromAuthentication(authentication)){ new SecurityAccessException();}
    return userMapper.toDTO(userRepository.findById(id).orElseThrow(ElemNotFound::new));
  }


  /**
   * отправить сообщение другу
   *
   * @param id  - номер пользователя
   * @param message  - сообщение другу
   * @return пользователь
   */
  @Override
  public void messageOfFriend(int id,String message) {
    UserEntity user = userRepository.findById(id).orElseThrow(ElemNotFound::new);
    Collection<String> getMessage1 = user.getMessage();
    getMessage1.add(message);
    user.setMessage(getMessage1);
  }
  /**
   * Пригласить в друзья
   *
   * @param nameUser  -  пользователь
   * @param nameFriends  -  пользователь друг
   * @return пользователь
   */
  @Override
  public void goFriend(String nameUser, String nameFriends) {
    int idFriend = (userRepository.findByName(nameFriends).orElseThrow(ElemNotFound::new))
            .getId();
    messageOfFriend(idFriend,"Пользователь :" + nameUser + "приглашает вас в друзья.");
    int myUserId = userRepository.findByName(nameUser).orElseThrow(ElemNotFound::new).getId();
    Friend friend = new Friend();
    friend.setUser1(myUserId);
    friend.setUser2(idFriend);
    friend.setStatus(StatusFriend.SUBSCRIPTION);
    friendsRepository.save(friend);


  }
  /**
   * добавить в друзья
   *
   * @param userId  -  номер пользователь
   * @param nameFriends  -  пользователь друг
   * @return пользователь
   */
  public void addFriend(int userId,String nameFriends) {
    UserEntity user = userRepository.findById(userId).orElseThrow(ElemNotFound::new);
    int idFriend = (userRepository.findByName(nameFriends).orElseThrow(ElemNotFound::new))
            .getId();
    Friend friend = friendsRepository.findByUser1AndUser2(userId,idFriend).orElseThrow(ElemNotFound::new);
    friend.setStatus(StatusFriend.FRIEND);

  }
  /**
   * добавить пользователя в подписку
   *
   * @param  authentication - логину пользователя
   * @param nameUser email - логину пользователя
   * @return пользователь
   */
  public void addSubscription(String nameUser, Authentication authentication) {
    UserEntity user = findEntityByEmail(authentication.getName());
    int idFriend = (userRepository.findByName(nameUser).orElseThrow(ElemNotFound::new))
            .getId();
    Friend friend = new Friend();
    friend.setUser1(user.getId());
    friend.setUser2(idFriend);
    friend.setStatus(StatusFriend.SUBSCRIPTION);
    friendsRepository.save(friend);
  }
  /**
   * Отписаться от пользователя
   *
   * @param  authentication - логину пользователя
   * @param nameUser email - логину пользователя
   * @return пользователь
   */
  public void deleteSubscription(String nameUser, Authentication authentication) {
    UserEntity user = findEntityByEmail(authentication.getName());
    int idFriend = (userRepository.findByName(nameUser).orElseThrow(ElemNotFound::new))
            .getId();
    Friend friend = friendsRepository.findByUser1AndUser2(user.getId(), idFriend).orElseThrow(ElemNotFound::new);
    friendsRepository.delete(friend);
  }
  /**
   * найти пользователя по email - логину
   *
   * @param email email - логину пользователя
   * @return пользователь
   */
  public UserEntity findEntityByEmail(String email) {
    log.info(FormLogInfo.getInfo());
    return userRepository.findByEmail(email).get();
  }


}

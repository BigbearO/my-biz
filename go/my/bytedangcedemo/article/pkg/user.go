package pkg

import (
	"bytedangcedemo/user/kitex_gen/user"
	"bytedangcedemo/user/kitex_gen/user/userservice"
	"context"
)

type UserManager struct {
	UserClient userservice.Client
}

func (u *UserManager) ArticleNumPlusOne(uid int64) error {
	_, err := u.UserClient.AddArticleNum(context.Background(), &user.AddArticleNumRequest{UserId: uid})
	return err
}

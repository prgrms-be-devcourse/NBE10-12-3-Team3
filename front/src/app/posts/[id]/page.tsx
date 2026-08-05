"use client";

import React, {useEffect, useState} from "react";
import {notFound, useRouter} from "next/navigation";
import Link from "next/link";
import {Bookmark, BookOpen, Calendar, Eye, Heart, Pencil, Trash2} from "lucide-react";
import {Avatar} from "@/components/ui/avatar";
import {BlurPaywall} from "@/components/common/blur-paywall";
import {useAuth} from "@/providers/auth-provider";
import {useHasMounted} from "@/hooks/use-has-mounted";
import {cn} from "@/lib/utils";
import {CommentList} from "@/components/comment/comment-list";
import {apiDelete, apiFetch, apiPost, resolveMediaUrl} from "@/lib/api";

interface Post {
    id: number;
    userId: number;
    nickname: string;
    seriesId: number | null;
    title: string;
    body: string | null;
    thumbnailUrl?: string;
    publishStatus: "PUBLIC" | "PRIVATE" | "DRAFT";
    accessLevel: "FREE" | "PAID";
    viewCount: number;
    likeCount: number;
    bookmarkCount: number;
    isLocked: boolean;
    isLiked: boolean;
    isBookmarked: boolean;
    createdAt: string;
    updatedAt: string;
}

export default function PostDetailPage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = React.use(params);
    const { isLoggedIn, user } = useAuth();
    const hasMounted = useHasMounted();
    const router = useRouter();
    const [post, setPost] = useState<Post | null>(null);
    const [loading, setLoading] = useState(true);
    const [liked, setLiked] = useState(false);
    const [bookmarked, setBookmarked] = useState(false);
    const [likeCount, setLikeCount] = useState(0);
    const [bookmarkCount, setBookmarkCount] = useState(0);

    useEffect(() => {
        apiFetch<Post>(`/api/posts/${id}`)
            .then((data) => {
                setPost(data);
                setLiked(data.isLiked);
                setBookmarked(data.isBookmarked);
                setLikeCount(data.likeCount);
                setBookmarkCount(data.bookmarkCount);
            })
            .catch(() => setPost(null))
            .finally(() => setLoading(false));
    }, [id]);

    if (loading) return <div className="min-h-screen bg-neutral-50 pt-20 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent mt-20" /></div>;
    if (!post) return notFound();

    // hasMounted 가드: 서버는 로그인 여부를 몰라 항상 비로그인으로 렌더링하므로,
    // 하이드레이션 이전엔 isLoggedIn을 그대로 쓰면 서버 HTML과 어긋납니다.
    const isAuthor = hasMounted && isLoggedIn && user?.id === post.userId;

    const handleDelete = async () => {
        if (!confirm("게시글을 삭제할까요?")) return;
        try {
            await apiDelete(`/api/posts/${id}`);
            router.push("/posts");
        } catch (err) {
            alert(err instanceof Error ? err.message : "삭제에 실패했습니다.");
        }
    };

    const handleLike = async () => {
        if (!isLoggedIn) return;
        try {
            if (liked) {
                await apiDelete(`/api/posts/${id}/likes`);
                setLiked(false);
                setLikeCount((prev) => prev - 1);
            } else {
                await apiPost(`/api/posts/${id}/likes`);
                setLiked(true);
                setLikeCount((prev) => prev + 1);
            }
        } catch (err) {
            console.error(err);
        }
    };

    const handleBookmark = async () => {
        if (!isLoggedIn) return;
        try {
            if (bookmarked) {
                await apiDelete(`/api/posts/${id}/bookmarks`);
                setBookmarked(false);
                setBookmarkCount((prev) => prev - 1);
            } else {
                await apiPost(`/api/posts/${id}/bookmarks`);
                setBookmarked(true);
                setBookmarkCount((prev) => prev + 1);
            }
        } catch (err) {
            console.error(err);
        }
    };

    return (
        <div className="min-h-screen bg-neutral-50 pb-20 pt-20">
            <div className="mx-auto max-w-[800px] px-4 sm:px-6">
                {/* 썸네일 */}
                {post.thumbnailUrl && (
                    <div className="mb-6 overflow-hidden rounded-xl">
                        <img src={resolveMediaUrl(post.thumbnailUrl)} alt="썸네일"
                             className="w-full max-h-[400px] object-cover"/>
                    </div>
                )}

                {/* 시리즈 정보 */}
                {post.seriesId && (
                    <Link href={`/series/${post.seriesId}`} className="inline-flex items-center gap-2 mb-4 px-3 py-1.5 bg-primary/10 text-primary rounded-full text-sm font-semibold hover:bg-primary/20 transition-colors">
                        <BookOpen className="h-3.5 w-3.5" />
                        시리즈 보기
                    </Link>
                )}

                {/* 제목 */}
                <h1 className="mb-4 text-3xl font-extrabold tracking-tight text-neutral-dark">
                    {post.title}
                </h1>

                {/* 작성자 & 메타 정보 */}
                <div className="mb-8 flex items-center justify-between border-b border-neutral-border pb-6">
                    <Link href={`/users/${post.userId}`} className="flex items-center gap-3 hover:opacity-80 transition-opacity">
                        <Avatar name={post.nickname} size="md" />
                        <div>
                            <p className="font-bold text-neutral-dark">{post.nickname}</p>
                            <div className="flex items-center gap-1 text-xs text-neutral-meta">
                                <Calendar className="h-3 w-3" />
                                {post.createdAt}
                            </div>
                        </div>
                    </Link>
                    <div className="flex items-center gap-4 text-neutral-meta">
                        <div className="flex items-center gap-1 text-sm">
                            <Eye className="h-4 w-4" />
                            {post.viewCount.toLocaleString()}
                        </div>
                        <button
                            onClick={handleLike}
                            className={cn(
                                "flex items-center gap-1 text-sm transition-colors",
                                liked ? "text-red-500" : "hover:text-red-400",
                                !isLoggedIn && "cursor-default opacity-60"
                            )}
                        >
                            <Heart className={cn("h-4 w-4", liked && "fill-red-500")} />
                            <span>{likeCount.toLocaleString()}</span>
                        </button>
                        <button
                            onClick={handleBookmark}
                            className={cn(
                                "flex items-center gap-1 text-sm transition-colors",
                                bookmarked ? "text-primary" : "hover:text-primary",
                                !isLoggedIn && "cursor-default opacity-60"
                            )}
                        >
                            <Bookmark className={cn("h-4 w-4", bookmarked && "fill-primary")} />
                            <span>{bookmarkCount.toLocaleString()}</span>
                        </button>
                    </div>
                </div>

                {/* 본문 */}
                {post.isLocked && typeof window !== 'undefined' && !window.location.search.includes('paymentKey') ? (
                    <BlurPaywall isLoggedIn={hasMounted && isLoggedIn} creatorName={post.nickname} />
                ) : (
                    <div className="prose max-w-none text-neutral-dark">
                        <div dangerouslySetInnerHTML={{ __html: post.body ?? "" }} />
                    </div>
                )}

                {/* 수정/삭제 버튼 */}
                {isAuthor && (
                    <div className="mt-10 flex justify-end gap-2 border-t border-neutral-100 pt-6">
                        <button
                            onClick={() => router.push(`/posts/${id}/edit`)}
                            className="flex items-center gap-1.5 rounded-lg border border-neutral-200 px-4 py-2 text-sm font-bold text-neutral-600 transition-colors hover:border-primary hover:text-primary"
                        >
                            <Pencil className="h-4 w-4" />
                            수정
                        </button>
                        <button
                            onClick={handleDelete}
                            className="flex items-center gap-1.5 rounded-lg border border-red-200 px-4 py-2 text-sm font-bold text-red-400 transition-colors hover:border-red-400 hover:text-red-600"
                        >
                            <Trash2 className="h-4 w-4" />
                            삭제
                        </button>
                    </div>
                )}

                {/* 댓글 */}
                <CommentList postId={Number(id)} isLocked={post.isLocked} />
            </div>
        </div>
    );
}

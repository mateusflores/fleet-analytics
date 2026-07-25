export interface PaginatedResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    pageable: {
        pageNumber: number;
        pageSize: number;
    };
    last: boolean;
    first: boolean;
    numberOfElements: number;
    size: number;
    number: number;
    sort: {
        sorted: boolean;
        unsorted: boolean;
        empty: boolean;
    };
    empty: boolean;
}
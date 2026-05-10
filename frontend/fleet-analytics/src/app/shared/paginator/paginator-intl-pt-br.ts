import { MatPaginatorIntl } from '@angular/material/paginator';

export function getPtBrPaginatorIntl(): MatPaginatorIntl {
  const paginatorIntl = new MatPaginatorIntl();
  paginatorIntl.itemsPerPageLabel = 'Itens por pagina:';
  paginatorIntl.nextPageLabel = 'Proxima pagina';
  paginatorIntl.previousPageLabel = 'Pagina anterior';
  paginatorIntl.firstPageLabel = 'Primeira pagina';
  paginatorIntl.lastPageLabel = 'Ultima pagina';
  paginatorIntl.getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return `0 de ${length}`;
    }

    const startIndex = page * pageSize;
    const endIndex = Math.min(startIndex + pageSize, length);
    return `${startIndex + 1} - ${endIndex} de ${length}`;
  };

  return paginatorIntl;
}
